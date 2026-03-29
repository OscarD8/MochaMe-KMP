package com.mochame.app.data.common

import co.touchlab.kermit.Logger
import com.mochame.app.data.local.room.entity.MutationEntryEntity
import com.mochame.app.data.local.toMochaException
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.sqlite.ExecutionPolicy
import com.mochame.app.domain.sync.MetadataStore
import com.mochame.app.domain.sync.MutationLedger
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.model.LocalFirstEntity
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.domain.sync.utils.MutationOp
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.infrastructure.sync.HLC
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import kotlinx.coroutines.flow.first


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
    protected val logger: Logger
) {

    /**
     * HLC generation.
     * The only place where the HLC is generated and the ledger is written.
     * It is private so subclasses cannot bypass the specialized gates below.
     */
    private suspend fun <R> dispatchIntent(
        candidateKey: String,
        op: MutationOp,
        businessAction: suspend (newHlc: HLC) -> R
    ): R {
        ensureReady()

        return try {
            executor.execute() {
                transactor.runImmediateTransaction {
                    val hlc = hlcFactory.getNextHlc()
                    val result = businessAction(hlc)

                    if (isGhostDelete(op, result)) return@runImmediateTransaction result

                    recordIntent(candidateKey, op, hlc)
                    updateModuleMetadata(hlc)
                    result
                }
            }
        } catch (e: Exception) {
            throw e.toMochaException()
        }
    }

    /**
     * Centralizes the timeout and error handling for the Janitor's boot sequence.
     */
    private suspend fun ensureReady() {
        val state = provider.bootState.first { it !is BootState.Initializing && it !is BootState.Idle }

        if (state is BootState.CriticalFailure) {
            // Maybe confirm that we definitely want to classify this as a sync error at this stage
            throw state.throwable ?: MochaException.Persistent.SyncInitializationException(state.error)
        }
    }

    private suspend fun recordIntent(candidateKey: String, op: MutationOp, hlc: HLC) {
        // Look for existing work that hasn't been synced yet
        val pending = mutationLedger.getPending(candidateKey, moduleName.tag)

        val effectiveCreatedAt = resolvePruningTimestamp(pending, op, hlc.ts)

        // Compaction: Remove the old intent before writing the new one
        pending?.let { mutationLedger.discardIntent(it.hlc.toString()) }

        mutationLedger.recordIntent(
            MutationEntryEntity(
                hlc = hlc,
                candidateKey = candidateKey,
                entityType = moduleName.tag,
                operation = op,
                syncStatus = SyncStatus.PENDING,
                createdAt = effectiveCreatedAt
            )
        )
    }

    /**
     * Recording an persistUpsert.
     * Enforces a sync ledger entry and an idempotent timestamp
     * towards the sync metadata and the local database action.
     * * Multiple local calls to update a single item still pending
     * sync will compact into a single server ping representing the latest state.
     */
    protected suspend fun persistUpsert(
        candidateKey: String,
        mergeLogic: suspend (newHlc: HLC) -> T,
        saveAction: suspend (T) -> Unit
    ): T {
        return dispatchIntent(candidateKey, MutationOp.UPSERT) { hlc ->
            val merged = mergeLogic(hlc)

            // Policy Enforcement
            val stamped = merged
                .withHlc(hlc)
                .withPhysicalTime(hlc.ts)

            saveAction(stamped)
            stamped
        }
    }

    /**
     * Recording soft deletion.
     * * Enforces a sync ledger entry and an idempotent timestamp
     * towards the sync metadata and the local database action.
     * * Calls to recordDelete that made no change to the local database
     * will not create a new ledger entry.
     * * If a pending deletion log entry already exists for the item,
     * the ledger timestamp will stick to the original deletion date to
     * enforce hard deletes and pruning of tombstone entries in accordance
     * with the initial intent.
     */
    protected suspend fun persistDelete(
        candidateKey: String,
        deleteAction: suspend (newHlc: HLC) -> Int
    ): Int {
        return dispatchIntent(candidateKey, MutationOp.DELETE) { hlc ->
            deleteAction(hlc)
        }
    }

    // -----------------------------------------------------------
    // HELPER FUNCTIONS
    // -----------------------------------------------------------

    private fun isGhostDelete(op: MutationOp, result: Any?): Boolean {
        return op == MutationOp.DELETE && (result as? Int) == 0
    }

    private fun resolvePruningTimestamp(
        pending: MutationEntryEntity?,
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
        metadataStore.recordMetadata(
            moduleName = moduleName,
            hlc = hlc
        )
    }

}