package com.mochame.sync.contract

import co.touchlab.kermit.Logger
import com.mochame.contract.boot.BootState
import com.mochame.contract.boot.BootStatusProvider
import com.mochame.contract.di.IoContext
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.exceptions.toMochaException
import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.contract.metadata.MutationOp
import com.mochame.contract.policy.ExecutionPolicy
import com.mochame.contract.providers.TransactionProvider
import com.mochame.logger.withTimer
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.LocalFirstEntity
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.serialization.FeatureCodecRegistry
import com.mochame.sync.contract.stores.BlobStager
import com.mochame.sync.contract.stores.SyncIntentStore
import com.mochame.sync.contract.stores.SyncModuleStateStore
import com.mochame.utils.KeyedLocker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

/**
 * The default logic for local-first data mutations.
 *
 * This chassis ensures that any change to local state is atomically bound
 * to a [SyncIntentEntity].
 *
 * @param T The entity type, adhering to the [LocalFirstEntity] contract.
 */
@Single(binds = [SyncReceiver::class])
abstract class LocalFirstRepository<T : LocalFirstEntity<T>>(
    protected val hlcFactory: HlcFactory,
    protected val executor: ExecutionPolicy,
    protected val codecRouter: FeatureCodecRegistry<T>,
    protected val locker: KeyedLocker,
    protected val syncIntentStore: SyncIntentStore,
    protected val syncModuleStateStore: SyncModuleStateStore,
    protected val logger: Logger,
    @IoContext protected val ioContext: CoroutineContext,
    protected val transactor: TransactionProvider,
    protected val blobStore: BlobStager,
    override val moduleContext: MochaModuleContext,
    private val provider: BootStatusProvider,
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
     * @param fetchExistingState used to perform a backup causality check, and possible ghost deleteBlobByHash.
     * @param computeChange requires the feature to assert the state change they wish to make. T is nullable in the case of deletions where a remote intent is made to delete state that does not exist locally.
     * @param persist after verifying and stamping the feature state change, the finalized state is persisted atomically alongside sync payloads/metadata.
     * @param onSkip offers a type-safe way to return R. Potential case of multiple concurrent requests to processing the same intent -
     * these will fail when accessing the database write lock, causing duplicate intents to [FeatureCodecRegistry.encode] a state that already exists, triggering onSkip.
     */
    protected suspend inline fun <R> processIntent(
        candidateKey: String,
        incomingHlc: HLC? = null, // does this need to be HLC.EMPTY?
        op: MutationOp,
        crossinline fetchExistingState: suspend () -> T?,
        crossinline computeChange: suspend (existing: T?) -> T?,
        crossinline persist: suspend (stamped: T) -> R,
        crossinline onSkip: (existing: T?) -> R
    ): R = withContext(ioContext) {

        ensureReady()
        val isRemoteIntent = incomingHlc != null

        locker.withLock(candidateKey) {
            executor.execute("[${moduleContext}_$op]") {
                val existingState = fetchExistingState()

                // 1. Initial state verification & LWW Checks
                if (existingState != null && !hlcFactory.isValid(existingState.hlc)) {
                    throw MochaException.Persistent.CorruptionDetected("Invalid HLC [${existingState.hlc}] for $candidateKey")
                }

                if (isRemoteIntent && existingState != null && incomingHlc <= existingState.hlc) {
                    logger.d { "Local item [$candidateKey / ${existingState.hlc}] rejected incoming $op [$incomingHlc]." }
                    return@execute onSkip(existingState)
                }

                // 2. Transformation & State Validation
                val provisionalState = computeChange(existingState)
                val validatedState =
                    validateMutationOrAbort(op, provisionalState, candidateKey)
                        ?: return@execute onSkip(existingState)

                // 3. HLC Advancement
                incomingHlc?.let { hlcFactory.witness(it) }
                val hlc = incomingHlc ?: hlcFactory.getNextHlc()
                val stampedState = validatedState.withHlc(hlc)

                // 4. Fork depending on commit strategy (encoding/staging/ledgering)
                return@execute if (isRemoteIntent) {
                    handleRemoteCommit(
                        candidateKey = candidateKey,
                        hlc = hlc,
                        persistAction = { persist(stampedState) }
                    )
                } else {
                    val payload = codecRouter.versionedEncode(stampedState, existingState)
                        ?: return@execute onSkip(existingState)
                    val summary = codecRouter.versionedSummarize(stampedState, existingState)

                    handleStagingAndLocalCommit(
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

    override suspend fun processRemoteIntent(
        context: DecodeContext,
        payload: ByteArray
    ) {
        // Use the repository's specific encoder to turn bytes into T
        val remoteEntity = codecRouter.versionedDecode(payload, context)

        processIntent(
            candidateKey = remoteEntity.id,
            incomingHlc = context.hlc,
            op = context.op,
            fetchExistingState = { fetch(context.id) },
            computeChange = { remoteEntity }, // The change is just the remote state
            persist = { stamped -> save(stamped) },
            onSkip = { old -> logger.v { "Remote intent skipped: $old." } }
        )
    }

    protected suspend inline fun <R> handleRemoteCommit(
        candidateKey: String,
        hlc: HLC,
        crossinline persistAction: suspend () -> R
    ): R {
        val mark = TimeSource.Monotonic.markNow()
        val result = transactor.runImmediateTransaction {
            // If a newer remote change arrives, any pending local
            // work for this key is now obsolete.
            // I just don't get why Gemini did the below but leaving here for now
//            syncIntentStore.getPendingByPrimaryKey(candidateKey, module)?.let {
//                syncIntentStore.discardIntent(it.hlc)
//            }

            updateHlcFloor(moduleContext.moduleName, hlc)
            val localResult = persistAction()

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

    protected suspend inline fun <R> handleStagingAndLocalCommit(
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
                updateHlcFloor(moduleContext.moduleName, hlc)
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
            } else {
                logger.e { "Local persistence failed: ${e.message}" }
            }

            throw e.toMochaException(e.message)
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
        val pending =
            syncIntentStore.getPendingByPrimaryKey(candidateKey, moduleContext.moduleName)

        val effectiveCreatedAt = resolvePruningTimestamp(pending, op, hlc.ts)

        // Compaction: Remove the old intent before writing the new one
        pending?.let {
            logger.i { "Compacting Intent | Replacing HLC [${it.hlc}] with [$hlc] for Key [$candidateKey]" }
            syncIntentStore.discardIntent(it.hlc)
        }

        syncIntentStore.recordIntent(
            SyncIntent(
                hlc = hlc,
                candidateKey = candidateKey,
                module = moduleContext.moduleName,
                model = moduleContext.modelName,
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
        pending: SyncIntent?,
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


    protected suspend fun updateHlcFloor(module: String, hlc: HLC) {
        syncModuleStateStore.updateHlcFloor(module, hlc)
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