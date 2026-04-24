package com.mochame.sync.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.platform.policies.ExecutionPolicy
import com.mochame.platform.providers.TransactionProvider
import com.mochame.di.IoContext
import com.mochame.metadata.BootState
import com.mochame.metadata.MochaModule
import com.mochame.metadata.MutationOp
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.metadata.BootStatusProvider
import com.mochame.sync.domain.PayloadEncoder
import com.mochame.sync.domain.SyncReceiver
import com.mochame.sync.domain.SyncStatus
import com.mochame.sync.domain.model.EntityMetadata
import com.mochame.sync.domain.model.LocalFirstEntity
import com.mochame.sync.domain.stores.BlobStager
import com.mochame.sync.domain.stores.MetadataStore
import com.mochame.sync.domain.stores.MutationLedger
import com.mochame.utils.exceptions.MochaException
import com.mochame.utils.exceptions.toMochaException
import com.mochame.logger.withTimer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

/**
 * The standard engine for local-first data mutations.
 *
 * This chassis ensures that any change to local state is atomically bound
 * to a "Sync Intent" in the mutation ledger.
 *
 * @param T The entity type, adhering to the [com.mochame.sync.domain.model.LocalFirstEntity] contract.
 */
abstract class LocalFirstRepository<T : LocalFirstEntity<T>>(
    protected val hlcFactory: HlcFactory,
    protected val executor: ExecutionPolicy,
    protected val encoder: PayloadEncoder<T>,
    protected val locker: KeyedLocker,
    protected val logger: Logger,
    @IoContext protected val ioContext: CoroutineContext,
    protected val transactor: TransactionProvider,
    protected val blobStore: BlobStager,
    protected val mutationLedger: MutationLedger,
    override val module: MochaModule,
    private val provider: BootStatusProvider,
    private val metadataStore: MetadataStore
) : SyncReceiver {

    /**
     * The anchor to wrap around all local persistence performed by any feature's repository, whether that
     * be as a result of a local UI event or a sync event.
     * * Ensures database lockouts are handled gracefully.
     * * A specialized locker is used to ensure that any single candidate key operation can only occur synchronously
     * in the case that a local operation is processing an intent at the same moment as a sync intent comes in.
     * @param candidateKey the item (either fetched remotely, or from a local UI event) to be persisted locally.
     * @param incomingHlc used when the SyncCoordinator is calling to process an intent. Forks how we process the intent.
     * @param op the DML operation for the intent. Required for metadata, logging, and state verification.
     * @param fetchOldState used to perform a backup causality check, and possible ghost deleteBlobByHash.
     * @param computeChange requires the feature to assert the state change they wish to make.
     * @param persist after verifying and stamping the feature state change, the finalized state is persisted atomically alongside sync payloads/metadata.
     * @param onSkip offers a type-safe way to return R.
     */
    protected suspend inline fun <R> processIntent(
        candidateKey: String,
        incomingHlc: HLC? = null,
        op: MutationOp,
        crossinline fetchOldState: suspend () -> T?,
        crossinline computeChange: suspend (old: T?) -> T?,
        crossinline persist: suspend (stamped: T) -> R,
        crossinline onSkip: (old: T?) -> R
    ): R = withContext(ioContext) {
        ensureReady()
        val isRemote = incomingHlc != null

        locker.withLock(candidateKey) {
            executor.execute("[${module}_$op]") {
                val oldState = fetchOldState()

                // 1. Initial state verification & LWW Checks
                if (oldState != null && !hlcFactory.isValid(oldState.hlc)) {
                    throw MochaException.Persistent.CorruptionDetected("Invalid HLC [${oldState.hlc}] for $candidateKey")
                }

                if (isRemote && oldState != null && incomingHlc <= oldState.hlc) {
                    logger.d { "Local item [$candidateKey / ${oldState.hlc}] rejected incoming $op [$incomingHlc]." }
                    return@execute onSkip(oldState)
                }

                // 2. Transformation & State Validation
                val provisionalState = computeChange(oldState)
                val validatedState =
                    validateMutationOrAbort(op, provisionalState, candidateKey)
                        ?: return@execute onSkip(oldState)

                // 3. HLC Advancement
                incomingHlc?.let { hlcFactory.witness(it) }
                val hlc = incomingHlc ?: hlcFactory.getNextHlc()
                val stampedState = validatedState.withHlc(hlc)

                // 4. Fork depending on commit strategy (encoding/staging/ledgering)
                return@execute if (isRemote) {
                    handleRemoteCommit(
                        candidateKey = candidateKey,
                        hlc = hlc,
                        persistAction = { persist(stampedState) }
                    )
                } else {
                    val payload = encoder.encode(stampedState, oldState)
                        ?: return@execute onSkip(oldState)
                    val summary = encoder.summarize(stampedState, oldState)

                    handleStagingAndCommit(
                        candidateKey = candidateKey,
                        op = op,
                        hlc = hlc,
                        payload = payload,
                        summary = summary,
                        persistAction = { persist(stampedState) }
                    )
                }
            }
        }
    }

    override suspend fun processRemoteChange(
        metadata: EntityMetadata,
        payload: ByteArray
    ) {
        val incomingHlc = HLC.parse(metadata.hlc.toString())

        // Use the repository's specific encoder to turn bytes into T
        val remoteEntity = encoder.decode(payload, metadata)

        // Funnel this into the unified processIntent engine
        processIntent(
            candidateKey = remoteEntity.id,
            incomingHlc = incomingHlc,
            op = metadata.op,
            fetchOldState = { fetch(metadata.id) },
            computeChange = { remoteEntity }, // The "change" is just the remote state
            persist = { stamped -> save(stamped) },
            onSkip = { old -> logger.v { "Confirmation: Sync skipped" } }
        )
    }

    protected suspend inline fun <R> handleRemoteCommit(
        candidateKey: String,
        hlc: HLC,
        crossinline persistAction: suspend () -> R
    ): R {
        val mark = TimeSource.Monotonic.markNow()
        val result = transactor.runImmediateTransaction {
            // CLEANUP: If a newer remote change arrives, any pending local
            // work for this key is now obsolete.
            mutationLedger.getPendingByKey(candidateKey, module)?.let {
                mutationLedger.discardIntent(it.hlc)
            }

            val localResult = persistAction()
            updateModuleMetadata(hlc) //
            localResult
        }

        logger.i { "Sync Applied | Key: $candidateKey | HLC: $hlc".withTimer(mark) }
        return result
    }

    // --- Features only required to implement these methods ---
    protected abstract suspend fun fetch(id: String): T?
    protected abstract suspend fun save(entity: T)

    // --- HELPERS ---
    /**
     * Primarily concerned with deletion intents, identifying ghost deletes and
     * a safety barrier against any other DML action that unexpectedly produces
     * null. If this is the case, the process execution cannot continue.
     */
    protected fun validateMutationOrAbort(
        op: MutationOp,
        provisionalState: T?,
        candidateKey: String,
    ): T? {
        if (op == MutationOp.DELETE && provisionalState == null) {
            logger.d { "Ghost Delete detected for $candidateKey. Aborting intent." }
            return null
        }
        if (provisionalState == null) {
            logger.d { "$candidateKey produced a null state for an intent out of a deleteBlobByHash context?.. Cannot stamp." }
            return null
        }
        return provisionalState
    }

    protected suspend inline fun <R> handleStagingAndCommit(
        candidateKey: String,
        op: MutationOp,
        hlc: HLC,
        payload: ByteArray,
        summary: String,
        crossinline persistAction: suspend () -> R
    ): R {
        val tMark = TimeSource.Monotonic.markNow()
        var blobId: String? = null
        var dbCommitted = false

        try {
            // 1: Check if External IO needed (bigger blob)
            if (payload.size > 64_000) {
                blobId = blobStore.stage(Buffer().also { it.write(payload) })
                logger.i { "Required staged payload: blobId $blobId [${payload.size / 1024}KB | Key: $candidateKey." }
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
            }.also {
                dbCommitted = true
                logger.v { "Local DB Transaction Committed".withTimer(mark) }
            }


            // 3. Commit the blob (sync intent commit means it cannot be orphaned)
            blobId?.also {
                blobStore.commit(it)
                logger.i {
                    "Intent Dispatched | Op: $op | Key: $candidateKey.".withTimer(tMark)
                }
            }

            return result
        } catch (e: Exception) {
            if (blobId != null) {
                if (!dbCommitted) {
                    blobStore.abort(blobId).also {
                        logger.e { "Mutation Failed: Blob Aborted | HLC: $hlc | BlobID: $it | Reason: ${e.message}" }
                    }
                } else {
                    logger.w(e) { "Post-Commit IO Failure: Blob $blobId stranded in /pending. Janitor will reconcile [${e.message}]." }
                }
            }

            throw e.toMochaException(candidateKey)
        }
    }

    protected suspend fun recordIntent(
        candidateKey: String,
        op: MutationOp,
        hlc: HLC,
        payload: ByteArray?,
        blobId: String?,
        diagnosticSummary: String
    ) {
        // Look for existing work that hasn't been synced yet
        val pending = mutationLedger.getPendingByKey(candidateKey, module)

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
                module = module,
                operation = op,
                syncStatus = SyncStatus.PENDING,
                createdAt = effectiveCreatedAt,
                payload = payload,
                overflowBlobId = blobId,
                diagnosticSummary = diagnosticSummary
            )
        )
    }

    protected fun resolvePruningTimestamp(
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

    protected suspend fun updateModuleMetadata(hlc: HLC) {
        metadataStore.recordPendingMetadata(
            module = module, hlc = hlc
        )
    }

    /**
     * Centralizes the timeout and error handling for the Janitor's boot sequence.
     */
    protected suspend fun ensureReady() {
        withTimeout(5_000L) {
            val state =
                provider.bootState.first { it !is BootState.Initializing && it !is BootState.Idle }

            if (state is BootState.CriticalFailure) {
                throw state.throwable
                    ?: MochaException.Persistent.BootInitializationError(state.error)
            }
        }
    }

}