package com.mochame.sync.api.repository

import co.touchlab.kermit.Logger
import com.mochame.logger.withTimer
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.api.exceptions.toMochaException
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.sync.api.models.HLC
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.spi.serialization.FeatureCodec
import com.mochame.sync.spi.infrastructure.SyncReceiver
import com.mochame.sync.spi.serialization.FeatureCodecRouter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The default logic for local-first data mutations.
 *
 * This chassis ensures that any change to local state is atomically bound
 * to a [SyncIntent].
 *
 * @param T The entity type, adhering to the [LocalFirstEntity] contract.
 */
@Single(binds = [SyncReceiver::class])
abstract class LocalFirstRepository<T : LocalFirstEntity<T>>(
    override val featureContext: FeatureContext,
    protected val codecRouter: FeatureCodecRouter<T, FeatureCodec<T>>,
    protected val deps: LocalFirstDependencies,
    protected val logger: Logger
) : SyncReceiver {

    /**
     * All local persistence performed by any feature's repository funnels through this method,
     * whether that be as a result of an outbound or an inbound intent.
     * * Ensures database lockouts are handled gracefully.
     * * A locker is used to ensure that any single candidate key operation can only occur synchronously
     * in the case that a local operation is processing an intent at the same moment as a sync intent comes in.
     * @param candidateKey the item (either fetched remotely, or from a local UI event) to be persisted locally.
     * @param incomingHlc used when the SyncCoordinator is calling to process an intent. Forks how we process the intent.
     * @param op the DML operation for the intent. Required for metadata, logging, and state verification.
     * @param fetchExistingState used to perform a backup causality check, and possible ghost deleteBlobByHash (?).
     * @param computeChange requires the feature to assert the state change they wish to make. T is nullable in the case of deletions where a remote intent is made to delete state that does not exist locally.
     * @param persist after verifying and stamping the feature state change, the finalized state is persisted atomically alongside sync payloads/metadata.
     * @param onSkip offers a type-safe way to return R. Potential case of multiple concurrent requests to processing the same intent -
     * these will fail when accessing the database write lock, causing duplicate intents to [FeatureCodecRouter.encode] a state that already exists, triggering onSkip.
     */
    protected suspend inline fun <R> processIntent(
        candidateKey: String,
        incomingHlc: HLC? = null,
        op: MutationOp,
        crossinline fetchExistingState: suspend () -> T?,
        crossinline computeChange: suspend (existing: T?) -> T?,
        crossinline persist: suspend (stamped: T) -> R,
        crossinline onSkip: (existing: T?) -> R
    ): R = withContext(deps.ioContext) {

        ensureReady()
        val isRemoteIntent = incomingHlc != null

        deps.locker.withLock(candidateKey) {
            deps.executor.execute("[${featureContext}_$op]") {
                val existingState = fetchExistingState()

                // Initial state verification & LWW Checks
                if (existingState != null && !deps.hlcFactory.isValid(existingState.hlc)) {
                    throw MochaException.Persistent.CorruptionDetected("Invalid HLC [${existingState.hlc}] for $candidateKey")
                }

                if (isRemoteIntent && existingState != null && incomingHlc <= existingState.hlc) {
                    logger.d { "Local item [$candidateKey / ${existingState.hlc}] rejected incoming $op [$incomingHlc]." }
                    return@execute onSkip(existingState)
                }

                // Transformation & State Validation
                val provisionalState = computeChange(existingState)
                val validatedState =
                    validateMutationOrAbort(op, provisionalState, candidateKey)
                        ?: return@execute onSkip(existingState)

                // HLC Advancement
                incomingHlc?.let { deps.hlcFactory.witness(it) }
                val hlc = incomingHlc ?: deps.hlcFactory.getNextHlc()
                val stampedState = validatedState.withHlc(hlc)

                // Fork depending on commit strategy (encoding/staging/ledgering)
                return@execute if (isRemoteIntent) {
                    handleRemoteCommit(
                        candidateKey = candidateKey,
                        hlc = hlc,
                        persistAction = { persist(stampedState) }
                    )
                } else {
                    val payload = codecRouter.routedEncode(stampedState, existingState)
                        ?: return@execute onSkip(existingState)
                    val summary =
                        codecRouter.versionedSummarize(stampedState, existingState)

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

    // If this process fails, we have the intent. It must be ensured that the intent record
    // is not updated to a status that will prune it until we have confirmation of the below
    override suspend fun processRemoteIntent(
        context: DecodeContext,
        payload: ByteArray,
    ) {
        // Use the repository's specific encoder to turn bytes into T
        val remoteEntity = codecRouter.routedDecode(payload, context)

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
        val result = deps.transactor.runImmediateTransaction {
            // If a newer remote change arrives, any pending local
            // work for this key is now obsolete.
            // I just don't get why Gemini did the below but leaving here for now
//            syncIntentStore.getPendingByCandidateKey(candidateKey, module)?.let {
//                syncIntentStore.discardIntent(it.hlc)
//            }

            deps.nodeManager.updateHlcFloor(hlc)
            val localResult = persistAction()

            localResult
        }

        logger.i { "SyncIntent persisted locally | Key: $candidateKey | HLC: $hlc".withTimer(mark) }
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
            // when does deleteBlobByHash come into it?
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
            // Check if External IO needed (bigger blob)
            if (payload.size > 64_000) {
                blobId = deps.blobStore.stage(Buffer().also { it.write(payload) })
                logger.i { "Required staged payload: blobId $blobId [${payload.size / 1024}KB | Key: $candidateKey." }
            }

            // Atomic DB Commit for sync intent & local persistence
            val mark = TimeSource.Monotonic.markNow()
            val result = deps.transactor.runImmediateTransaction {
                val localResult = persistAction()
                recordIntent(
                    candidateKey,
                    op,
                    hlc,
                    if (blobId == null) payload else null,
                    blobId,
                    summary
                )
                deps.nodeManager.updateHlcFloor(hlc)
                localResult
            }.also {
                dbCommitted = true
                deps.invalidationHook.invalidate()
                logger.v { "Local DB Transaction Committed".withTimer(mark) }
            }

            // Commit the blob (sync intent commit means it cannot be orphaned)
            blobId?.also {
                deps.blobStore.commit(it)
                logger.i {
                    "Intent Dispatched | Op: $op | Key: $candidateKey.".withTimer(tMark)
                }
            }

            return result
        } catch (e: Exception) {
            if (blobId != null) {
                // if an exception happened and we have an overflow
                if (!dbCommitted) {
                    deps.blobStore.abort(blobId).also {
                        logger.e { "Mutation Failed: Blob Aborted | HLC: $hlc | BlobID: $it | Reason: ${e.message}" }
                    }
                } else {
                    logger.w(e) { "Post-Commit IO Failure: Blob $blobId stranded in /pending. Janitor will reconcile [${e.message}]." }
                    // Update the status of the SyncIntent? New status?
                    throw MochaException.Transient.BlobResolutionPending(blobId)
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
        val pending = deps.intentStore.getPendingByCandidateKey(candidateKey)

        val effectiveCreatedAt = resolvePruningTimestamp(pending, op, hlc.ts)

        // Compaction
        pending?.let {
            logger.i { "Compacting Intent | Replacing HLC [${it.hlc}] with [$hlc] for Key [$candidateKey]" }
            deps.intentStore.discardIntent(it.hlc)
        }

        deps.intentStore.recordIntent(
            SyncIntent(
                featureSchemaVersion = codecRouter.latestVersion, // guaranteed to not have changed
                hlc = hlc,
                candidateKey = candidateKey,
                featureContext = featureContext,
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
            // Don't reset the pruning clock for double-deletes
            pending.createdAt
        } else {
            now
        }
    }

    /**
     * Timeout and error handling for the Janitor's boot sequence.
     */
    protected suspend fun ensureReady() {
        withTimeout(5_000L.milliseconds) {
            val state =
                deps.bootStatus.bootState.first { it !is BootState.Initializing && it !is BootState.Idle }

            if (state is BootState.CriticalFailure) {
                throw state.exception
                    ?: MochaException.Persistent.BootInitializationError(state.error)
            }
        }
    }

}