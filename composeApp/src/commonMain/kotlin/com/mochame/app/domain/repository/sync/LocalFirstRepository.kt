package com.mochame.app.domain.repository.sync

import androidx.room.Transactor
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import com.mochame.app.core.HLC
import com.mochame.app.core.HlcFactory
import com.mochame.app.core.MochaModule
import com.mochame.app.core.MutationOp
import com.mochame.app.core.SyncInitializationException
import com.mochame.app.core.SyncStatus
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.sync.MutationLedgerDao
import com.mochame.app.database.dao.sync.SyncMetadataDao
import com.mochame.app.database.entity.MutationEntryEntity
import com.mochame.app.domain.system.BootState
import com.mochame.app.domain.system.BootStatusProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout


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
    private val database: MochaDatabase,
    private val ledgerDao: MutationLedgerDao,
    private val metadataDao: SyncMetadataDao,
    private val moduleName: MochaModule,
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
        return database.useWriterConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                val hlc = hlcFactory.getNextHlc()
                val result = businessAction(hlc)

                if (isGhostDelete(op, result)) return@withTransaction result

                recordIntent(candidateKey, op, hlc)
                updateModuleMetadata(hlc)
                result
            }
        }
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

            // Policy Enforcement: Stamping is non-negotiable here
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

    private suspend fun recordIntent(candidateKey: String, op: MutationOp, hlc: HLC) {
        // Look for existing work that hasn't been synced yet
        val pending = ledgerDao.getPendingMutation(candidateKey, moduleName.tag)

        val effectiveCreatedAt = resolvePruningTimestamp(pending, op, hlc.ts)

        // Compaction: Remove the old intent before writing the new one
        pending?.let { ledgerDao.deleteByHlc(it.hlc.toString()) }

        ledgerDao.upsert(
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
        metadataDao.recordLocalMutation(
            moduleName = moduleName.toString(),
            hlc = hlc,
            now = hlc.ts
        )
    }

    /**
     * Centralizes the timeout and error handling for the Janitor's boot sequence.
     */
    private suspend fun ensureReady() {
        try {
            withTimeout(5000L) {
                provider.bootState.first { it is BootState.Ready }
            }
        } catch (e: TimeoutCancellationException) {
            logger.e { "Sync stalled. Unable to run dispatchIntent. $e." }
            throw SyncInitializationException("An error occurred during system boot. $e.")
        }
    }

}

