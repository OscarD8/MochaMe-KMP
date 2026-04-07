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
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.sync.HLC
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import com.mochame.app.infrastructure.utils.toMochaException
import com.mochame.app.infrastructure.utils.withTimer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
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
     *
     * All Deletion OPs MUST return int
     */
    protected suspend fun <R> dispatchIntent(
        candidateKey: String,
        op: MutationOp,
        fetchOldState: suspend () -> T?,
        computeAndStamp: suspend (old: T?, newHlc: HLC) -> T?,
        persist: suspend (stamped: T) -> R
    ): R {
        ensureReady()

        return executor.execute("[${moduleName}_$op]") {
            // Phase 1: Context hydration
            val hlc = hlcFactory.getNextHlc()
            val oldState = fetchOldState()
            validateHlcIntegrity(candidateKey, oldState)

            // Phase 2: Transformation / Validation / Encoding
            val computedState = computeAndStamp(oldState, hlc)
            val newState = validateMutationOrAbort(op, computedState, candidateKey)
                ?: return@execute 0 as R // All Deletion OPs MUST return int

            val payload = encoder.encode(newState, oldState)
                ?: return@execute newState as R // No changes detected, return the old state
            val summary = encoder.summarize(newState, oldState)

            // Phase 3: Staging & Persistence
            return@execute handleStagingAndCommit(
                candidateKey = candidateKey,
                op = op,
                hlc = hlc,
                payload = payload,
                summary = summary,
                persistAction = { persist(newState) }
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

    private fun validateHlcIntegrity(candidateKey: String, oldState: T?) {
        if (oldState != null && !hlcFactory.isValid(oldState.hlc)) {
            throw MochaException.Persistent.CorruptionDetected(candidateKey)
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
        payload: ByteArray,
        summary: String,
        persistAction: suspend () -> R
    ): R {
        var blobId: String? = null
        val tMark = TimeSource.Monotonic.markNow()

        try {
            // 1: Check if External IO needed (big blob)
            if (payload.size > 64_000) {
                /*
                    I think this temporarily spikes memory usage but allows
                    stage to be ubiquitous and work in chunks. Should only be
                    working with exceptional cases of metadata here.
                 */
                blobId = blobStore.stage(Buffer().apply { write(payload) })
                logger.d {
                    ("Intent Payload Staged | HLC: $hlc | BlobID: $blobId | Size: ${payload.size / 1024}KB.")
                }
            }

            // 2: Atomic DB Commit for sync intent & local persistence
            val mark = TimeSource.Monotonic.markNow()
            val result = transactor.runImmediateTransaction {
                val localResult = persistAction()
                recordIntent(
                    candidateKey,
                    op,
                    hlc,
                    if (blobId == null) payload else null,
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
        inlinePayload: ByteArray?,
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
                payload = inlinePayload,
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