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
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.spi.infrastructure.serialization.FeatureCodec
import com.mochame.sync.spi.infrastructure.SyncReceiver
import com.mochame.sync.spi.infrastructure.serialization.FeatureCodecRouter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The default logic for local-first data mutations.
 * Ensures any change to local state is atomically bound to a [SyncIntent].
 *
 * @param T The entity type, adhering to the [LocalFirstEntity] contract.
 */
@Single(binds = [SyncReceiver::class])
abstract class LocalFirstRepository<T : LocalFirstEntity<T>>(
    override val featureContext: FeatureContext,
    @PublishedApi internal val deps: LocalFirstDependencies,
    @PublishedApi internal val codec: FeatureCodecRouter<T, FeatureCodec<T>>,
    protected val logger: Logger
) : SyncReceiver { // composition would have been better

    /**
     * All local persistence performed by any feature's repository funnels through this method,
     * whether that be as a result of an outbound or an inbound intent.
     * * Ensures database lockouts are handled gracefully.
     * * A locker is used to ensure that any single candidate key operation is sequential
     * in the case that a local operation is processing an intent at the same moment as a remote intent comes in.
     * The current design holds any potential delay of the executor within the mutex lock.
     * * Remote processing expects a batch process, and therefore requires the execution policy and
     * any Transactor declarations to be declared at the batch orchestration level.
     * @param candidateKey the item (either fetched remotely, or from a local UI event) to be persisted locally.
     * @param incomingHlc used when the SyncCoordinator is calling to process an intent. Forks how we process the intent.
     * @param op the DML operation for the intent. Required for metadata, logging, and state verification.
     * @param fetchExistingState used to perform a backup causality check, and possible ghost deleteBlobByHash (?).
     * @param computeChange requires the feature to assert the state change they wish to make. T is nullable in the case of deletions where a remote intent is made to delete state that does not exist locally.
     * @param persist after verifying and stamping the feature state change, the finalized state is persisted atomically alongside sync payloads/metadata.
     * @param onSkip offers a type-safe way to return R. Potential case of multiple concurrent requests to processing the same intent -
     * these will fail when accessing the database write lock, causing duplicate intents to [FeatureCodecRouter.routedEncode] a state that already existsInCommitted, triggering onSkip.
     *
     * * [R] Return type of any persistence definition, allowing differentiation between
     * the type of data being processed and the type to be returned (e.g. a delete count).
     * * [T] The defined [LocalFirstEntity] involved in processing existing model confirmation, compaction, [HLC] stamping, and local persistence.
     */
    @PublishedApi
    internal suspend inline fun <R> processIntent(
        candidateKey: String,
        incomingHlc: HLC? = null,
        op: MutationOp,
        crossinline fetchExistingState: suspend () -> T?,
        crossinline computeChange: suspend (existing: T?) -> T,
        crossinline persist: suspend (stamped: T) -> R,
        crossinline onSkip: (fallback: T) -> R
    ): R = withContext(deps.ioContext) {
        ensureReady()

        deps.locker.withLock(candidateKey) {
            if (incomingHlc != null) {
                executeIntentPipeline(
                    candidateKey = candidateKey,
                    incomingHlc = incomingHlc,
                    op = op,
                    fetchExistingState = fetchExistingState,
                    computeChange = computeChange,
                    persist = persist,
                    onSkip = onSkip
                )
            } else {
                deps.executor.execute("[${featureContext}_$op]") {
                    executeIntentPipeline(
                        candidateKey = candidateKey,
                        incomingHlc = incomingHlc,
                        op = op,
                        fetchExistingState = fetchExistingState,
                        computeChange = computeChange,
                        persist = persist,
                        onSkip = onSkip
                    )
                }
            }
        }
    }

    @PublishedApi
    internal suspend inline fun <R> executeIntentPipeline(
        candidateKey: String,
        incomingHlc: HLC?,
        op: MutationOp,
        crossinline fetchExistingState: suspend () -> T?,
        crossinline computeChange: suspend (existing: T?) -> T,
        crossinline persist: suspend (stamped: T) -> R,
        crossinline onSkip: (fallback: T) -> R
    ): R {
        val existingState = fetchExistingState()

        validateLwwRejection(
            existingState, incomingHlc, candidateKey
        )?.let { rejectedState -> return onSkip(rejectedState) }

        if (op == MutationOp.DELETE && isGhostDelete(existingState, candidateKey))
            return onSkip(existingState!!)

        val candidateState = computeChange(existingState)

        return if (incomingHlc != null) {
            persist(candidateState)
        } else {
            val stampedState = candidateState.withHlc(deps.hlcFactory.getNextHlc())

            handleLocalCommit(
                candidateKey = candidateKey,
                op = op,
                stampedState = stampedState,
                existingState = existingState,
                persistAction = { persist(stampedState) },
                onSkipAction = { onSkip(stampedState) }
            )
        }
    }


    // -----------------------------------------------------------
    // ACCESS
    // -----------------------------------------------------------
    protected suspend inline fun localUpsert(
        candidateKey: String,
        incomingHlc: HLC? = null,
        op: MutationOp = MutationOp.UPSERT,
        crossinline fetchExistingState: suspend () -> T?,
        crossinline computeChange: suspend (existing: T?) -> T,
        crossinline persist: suspend (stamped: T) -> T,
        crossinline onSkip: (fallback: T) -> T = { it }
    ): T = processIntent(
        candidateKey,
        incomingHlc,
        op,
        fetchExistingState,
        computeChange,
        persist,
        onSkip
    )

    /**
     * Convenience overload providing the default soft-delete logic.
     * Suspend functional parameters with default values are not yet supported in inline functions.
     */
    protected suspend inline fun localDelete(
        candidateKey: String,
        incomingHlc: HLC? = null,
        crossinline fetchExistingState: suspend () -> T?,
        crossinline persist: suspend (stamped: T) -> Int,
        crossinline onSkip: (fallback: T) -> Int = { 0 }
    ): Int = localDelete(
        candidateKey = candidateKey,
        incomingHlc = incomingHlc,
        fetchExistingState = fetchExistingState,
        computeChange = { it!!.markDeleted() },
        persist = persist,
        onSkip = onSkip
    )

    protected suspend inline fun localDelete(
        candidateKey: String,
        incomingHlc: HLC? = null,
        crossinline fetchExistingState: suspend () -> T?,
        crossinline computeChange: suspend (existing: T?) -> T,
        crossinline persist: suspend (stamped: T) -> Int,
        crossinline onSkip: (fallback: T) -> Int = { 0 }
    ): Int = processIntent(
        candidateKey = candidateKey,
        incomingHlc = incomingHlc,
        op = MutationOp.DELETE,
        fetchExistingState = fetchExistingState,
        computeChange = computeChange,
        persist = persist,
        onSkip = onSkip
    )

    /**
     * If this process fails, the intent is persisted already. It must be ensured that the intent record
     * is not updated to a status that marks it for pruning until confirmation of the below.
     */
    override suspend fun processRemoteIntent(
        context: DecodeContext,
        payload: ByteArray?,
    ) {
        if (payload == null) {
            if (context.overflowBlobId == null) {
                logger.e { "Should not have received null payload with no overflowId for ${context.candidateKey}." }
                throw MochaException.Persistent.CorruptionDetected("Should not have received null payload with no overflowId for ${context.candidateKey}")
            }
            // Intent is persisted by Coordinator
            logger.d { "Branching to overflow processing. [Key: ${context.candidateKey}] [blobId: ${context.overflowBlobId}]." }
        }

        processIntent(
            candidateKey = context.candidateKey,
            incomingHlc = context.hlc,
            op = context.op,
            fetchExistingState = { fetch(context.candidateKey) },
            computeChange = { codec.routedDecode(payload!!, context, it) },
            persist = { stamped -> save(stamped) },
            onSkip = { logger.v { "Remote intent skipped. ID:${it.id}. HLC: ${it.hlc}" } }
        )
    }

    // --- Features required to implement these methods ---
    protected abstract suspend fun fetch(id: String): T?
    protected abstract suspend fun save(entity: T)
    protected abstract suspend fun compactState(newState: T, existing: T?): T


    // -----------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------
    @PublishedApi
    internal fun validateLwwRejection(
        existingState: T?,
        incomingHlc: HLC?,
        candidateKey: String
    ): T? {
        if (existingState == null) return null

        deps.hlcFactory.assertValid(existingState.hlc, candidateKey)

        if (incomingHlc != null && incomingHlc <= existingState.hlc) {
            logger.d { "Local item [$candidateKey / ${existingState.hlc}] rejected incoming $incomingHlc." }
            return existingState
        }

        return null
    }

    @PublishedApi
    internal fun isGhostDelete(existing: T?, candidateKey: String): Boolean {
        if (existing == null)
            throw MochaException.Persistent.StateIssue("Delete attempt against non-existent record. Key: $candidateKey.")

        if (existing.isDeleted) {
            logger.d { "Ghost Delete detected for $candidateKey. Aborting intent." }
            return true
        }
        return false
    }

    @PublishedApi
    internal suspend fun <R> handleLocalCommit(
        candidateKey: String,
        op: MutationOp,
        stampedState: T,
        existingState: T?,
        persistAction: suspend () -> R,
        onSkipAction: () -> R
    ): R {
        val payload = codec.routedEncode(stampedState, existingState)
            ?: return onSkipAction()

        val summary = codec.routedSummarize(stampedState, existingState)
        val hlc = stampedState.hlc
        val tMark = TimeSource.Monotonic.markNow()

        var blobId: String? = null
        var dbCommitted = false

        try {
            if (payload.size > 64_000) {
                blobId = deps.blobStore.stage(Buffer().also { it.write(payload) })
                logger.i { "Required staged payload: blobId $blobId [${payload.size / 1024}KB | Key: $candidateKey]" }
            }

            val mark = TimeSource.Monotonic.markNow()

            val result = deps.transactor.runImmediateTransaction {
                val localResult = persistAction()
                recordIntent(
                    candidateKey = candidateKey,
                    op = op,
                    hlc = hlc,
                    payload = if (blobId == null) payload else null,
                    blobId = blobId,
                    diagnosticSummary = summary
                )
                deps.nodeManager.updateHlcFloor(hlc)
                localResult
            }.also {
                dbCommitted = true
                deps.invalidationHook.invalidate()
                logger.v { "Local DB Transaction Committed".withTimer(mark) }
            }

            blobId?.also {
                deps.blobStore.commit(it)
                logger.i {
                    "Intent Dispatched | Op: $op | Key: $candidateKey".withTimer(tMark)
                }
            }

            return result
        } catch (e: Exception) {
            if (blobId != null) {
                if (!dbCommitted) {
                    deps.blobStore.abort(blobId).also {
                        logger.e { "Mutation Failed: Blob Aborted | HLC: $hlc | BlobID: $it | Reason: ${e.message}" }
                    }
                } else {
                    logger.w(e) { "Post-Commit IO Failure: Blob $blobId stranded in /pending. Janitor will reconcile [${e.message}]." }
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
        deps.intentStore.recordIntent(
            SyncIntent(
                featureSchemaVersion = codec.latestVersion, // guaranteed to not have changed
                hlc = hlc,
                candidateKey = candidateKey,
                featureContext = featureContext,
                operation = op,
                syncStatus = SyncStatus.PENDING,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                payload = payload,
                overflowBlobId = blobId,
                diagnosticSummary = diagnosticSummary
            )
        )
    }

    /**
     * Originally existed for compaction of SyncIntent models, but
     * as each intent only holds the implicit change in its payload,
     * current design now does not compact pending intents before sync
     * and will sync all changes causally.
     */
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
    @PublishedApi
    internal suspend fun ensureReady() {
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