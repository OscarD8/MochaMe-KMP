package com.mochame.app.data.common

import co.touchlab.kermit.Logger
import com.mochame.app.data.local.room.entities.SyncIntentEntity
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.sync.LocalFirstEntity
import com.mochame.app.domain.sync.PayloadEncoder
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.stores.BlobStager
import com.mochame.app.domain.sync.stores.MetadataStore
import com.mochame.app.domain.sync.stores.MutationLedger
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.domain.sync.utils.MutationOp
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.domain.system.sqlite.ExecutionPolicy
import com.mochame.app.infrastructure.sync.HLC
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import com.mochame.app.infrastructure.utils.toMochaException
import com.mochame.app.infrastructure.utils.withTimer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.time.TimeSource


/**
 * The standard engine for local-first data mutations.
 *
 * This chassis ensures that any change to local state is atomically bound
 * to a "Sync Intent" in the mutation ledger.
 *
 * @param T The entity type, adhering to the [LocalFirstEntity] contract.
 */
abstract class LocalFirstRepository<T : LocalFirstEntity<T>>(
    private val hlcFactory: HlcFactory,
    private val provider: BootStatusProvider,
    private val transactor: TransactionProvider,
    private val mutationLedger: MutationLedger,
    private val metadataStore: MetadataStore,
    private val moduleName: MochaModule,
    private val executor: ExecutionPolicy,
    private val blobStore: BlobStager,
    private val encoder: PayloadEncoder<T>,
    protected val logger: Logger
) {

    /**
     * HLC generation.
     * The only place where the HLC is generated and the ledger is written.
     * It is private so subclasses cannot bypass the specialized gates below.
     */
    protected suspend fun <R> dispatchIntent(
        candidateKey: String,
        op: MutationOp,
        fetchOldState: suspend () -> T?,
        computeAndStamp: suspend (old: T?, newHlc: HLC) -> T?,
        persist: suspend (stamped: T) -> R,
        onSkip: (old: T?) -> R
    ): R {
        ensureReady()

        return executor.execute("[${moduleName}_$op]") {
            // Phase 1: Context hydration
            val oldState = fetchOldState()

            if (oldState != null && !hlcFactory.isValid(oldState.hlc)) {
                throw MochaException.Persistent.CorruptionDetected("Invalid HLC for $candidateKey")
            }

            // Phase 2: Transformation / Validation / Encoding
            val computedState = computeAndStamp(oldState, HLC.EMPTY)

            val newState = validateMutationOrAbort(op, computedState, candidateKey)
                ?: return@execute onSkip(oldState) // All Deletion OPs MUST return int

            val hlc = hlcFactory.getNextHlc()
            if (oldState != null && hlc <= oldState.hlc) {
                // Placing this here for testing to confirm the HLC logic works in the repository context
                logger.e {
                    "Causality Violation | Key: $candidateKey | " +
                            "New HLC [$hlc] is not greater than Old HLC [${oldState.hlc}]"
                }

                // If this log is ever triggered, HLCFactory is broken as you have pulled an
                // old state with an HLC greater than the factory's output. Maybe add a tick option
            }

            val finalState = newState.withHlc(hlc = hlc)

            val bufferPayload = encoder.encode(newState, oldState)
                ?: return@execute onSkip(oldState) // No changes detected, return the old state

            val summary = encoder.summarize(newState, oldState)

            // Phase 3: Staging & Persistence
            return@execute handleStagingAndCommit(
                candidateKey = candidateKey,
                op = op,
                hlc = hlc,
                payloadBuffer = bufferPayload,
                summary = summary,
                persistAction = { persist(finalState) }
            )
        }
    }

    /**
     * Centralizes the timeout and error handling for the Janitor's boot sequence.
     */
    private suspend fun ensureReady() {
        withTimeout(5_000L) {
            val state =
                provider.bootState.first { it !is BootState.Initializing && it !is BootState.Idle }

            if (state is BootState.CriticalFailure) {
                throw state.throwable
                    ?: MochaException.Persistent.BootInitializationError(state.error)
            }
        }
    }

    private fun validateMutationOrAbort(
        op: MutationOp,
        newState: T?,
        candidateKey: String
    ): T? {
        // 1. Detect Ghost Delete
        if (op == MutationOp.DELETE && newState == null) {
            logger.d { "Ghost Delete detected for $candidateKey. Aborting intent." }
            return null
        }

        // 2. Validate Successful Computation
        return newState ?: throw MochaException.Persistent.CorruptionDetected(
            "Compute yielded null during an UPSERT for $candidateKey."
        )
    }

    private suspend fun <R> handleStagingAndCommit(
        candidateKey: String,
        op: MutationOp,
        hlc: HLC,
        payloadBuffer: Buffer,
        summary: String,
        persistAction: suspend () -> R
    ): R {
        val tMark = TimeSource.Monotonic.markNow()
        var blobId: String? = null

        try {
            // 1: Check if External IO needed (big blob)
            val inlineBytes = if (payloadBuffer.size > 64_000) {
                blobId = blobStore.stage(payloadBuffer)
                null
            } else {
                payloadBuffer.readByteArray() // Consume buffer for DB
            }

            // 2: Atomic DB Commit for sync intent & local persistence
            val mark = TimeSource.Monotonic.markNow()
            val result = transactor.runImmediateTransaction {
                val localResult = persistAction()
                recordIntent(
                    candidateKey,
                    op,
                    hlc,
                    inlineBytes,
                    blobId,
                    summary
                )
                updateModuleMetadata(hlc)
                localResult
            }
            logger.v { "Local DB Transaction Committed".withTimer(mark) }

            // 3. Commit the blob (sync intent commit means it cannot be orphaned)
            blobId?.let { blobStore.commit(it) }

            logger.i {
                "Intent Dispatched | Op: $op | Key: $candidateKey.".withTimer(tMark)
            }
            return result
        } catch (e: Exception) {
            blobId?.let {
                blobStore.abort(it)
                logger.w { "Mutation Failed: Blob Aborted | HLC: $hlc | BlobID: $it | Reason: ${e.message}" }
            }
            throw e.toMochaException(candidateKey)
        }
    }

    private suspend fun recordIntent(
        candidateKey: String,
        op: MutationOp,
        hlc: HLC,
        payload: ByteArray?,
        blobId: String?,
        diagnosticSummary: String
    ) {
        // Look for existing work that hasn't been synced yet
        val pending = mutationLedger.getPendingByKey(candidateKey, moduleName)

        val effectiveCreatedAt = resolvePruningTimestamp(pending, op, hlc.ts)

        // Compaction: Remove the old intent before writing the new one
        pending?.let {
            logger.i { "Compacting Ledger | Replacing HLC [${it.hlc}] with [$hlc] for Key [$candidateKey]" }
            mutationLedger.discardIntent(it.hlc)
        }

        mutationLedger.recordIntent(
            SyncIntentEntity(
                hlc = hlc.toString(),
                candidateKey = candidateKey,
                module = moduleName,
                operation = op,
                syncStatus = SyncStatus.PENDING,
                createdAt = effectiveCreatedAt,
                payload = payload,
                overflowBlobId = blobId,
                diagnosticSummary = diagnosticSummary
            )
        )
    }

    private fun resolvePruningTimestamp(
        pending: SyncIntentEntity?,
        currentOp: MutationOp,
        now: Long
    ): Long {
        return if (pending != null && currentOp == MutationOp.DELETE
            && pending.operation == MutationOp.DELETE
        ) {
            // Preservation: Don't reset the pruning clock for double-deletes
            pending.createdAt
        } else {
            now
        }
    }

    private suspend fun updateModuleMetadata(hlc: HLC) {
        metadataStore.recordPendingMetadata(
            module = moduleName, hlc = hlc
        )
    }

}