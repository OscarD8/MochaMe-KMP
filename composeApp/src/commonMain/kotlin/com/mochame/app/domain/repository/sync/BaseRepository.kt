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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout


/**
 * The standard engine for local-first data mutations.
 *
 * This chassis ensures that any change to local state is atomically bound
 * to a "Sync Intent" in the mutation ledger.
 *
 * @param T The entity type, adhering to the [LocalFirstEntity] contract.
 */
abstract class BaseRepository<T : LocalFirstEntity<T>>(
    private val hlcFactory: HlcFactory,
    private val database: MochaDatabase,
    private val ledgerDao: MutationLedgerDao,
    private val metadataDao: SyncMetadataDao,
    private val moduleName: MochaModule,
    private val janitor: SyncJanitor,
    protected val logger: Logger
) {
    /**
     * The primary entry point for all database writes.
     *
     * **Control Rules:**
     * 1. **Atomicity:** The business action and ledger entry are wrapped in a single transaction.
     * 2. **Write Contention:** Do not perform heavy computation (mapping, parsing, network)
     * inside [businessAction]. Execute only Room DAO calls.
     * 3. **Monotonicity:** The [newHlc] is generated immediately before the write to
     * ensure causality.
     *
     * @param candidateKey The UUID of the record being modified.
     * @param op The type of operation (UPSERT or DELETE).
     * @param businessAction The DAO call. Must return [Int] (affected rows) for DELETES.
     */
    protected suspend fun <R> commitWithIntent(
        candidateKey: String,
        op: MutationOp,
        businessAction: suspend (newHlc: HLC) -> R
    ): R {
        // 1. Ensure the HLC and Janitor are ready
        ensureReady()

        return database.useWriterConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                // 2. STAMP: Generate the device's timestamp
                val hlc = hlcFactory.getNextHlc()

                // 3. EXECUTE: Perform the actual database write
                val result = businessAction(hlc)

                // 4. GUARD: If a delete found nothing to delete, do not log an intent.
                if (isGhostDelete(op, result)) return@withTransaction result

                // 5. LEDGER: Atomic compaction and intent logging
                recordIntent(candidateKey, op, hlc)

                // 6. METADATA: Update module-level high-water marks
                updateModuleMetadata(hlc)

                result
            }
        }
    }

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
                janitor.isInitialized.await()
            }
        } catch (e: TimeoutCancellationException) {
            logger.e { "Critical: SyncJanitor timeout. System stalled: $e" }
            throw SyncInitializationException("Startup timeout. Please retry. $e")
        } catch (e: Exception) {
            logger.e(e) { "Mutation blocked: Janitor reported failure." }
            throw e
        }
    }

}

