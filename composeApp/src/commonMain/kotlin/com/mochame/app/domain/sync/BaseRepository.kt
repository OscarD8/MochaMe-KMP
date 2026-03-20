package com.mochame.app.domain.sync

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.mochame.app.core.HlcFactory
import com.mochame.app.core.MochaModule
import com.mochame.app.core.MutationOp
import com.mochame.app.core.SyncStatus
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.MutationLedgerDao
import com.mochame.app.database.entity.MutationEntryEntity
import com.mochame.app.domain.model.LocalFirstEntity


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
    private val moduleName: MochaModule
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
     * @param entityId The UUID of the record being modified.
     * @param op The type of operation (UPSERT or DELETE).
     * @param businessAction The DAO call. Must return [Int] (affected rows) for DELETES.
     */
    protected suspend fun <R> mutate(
        entityId: String,
        op: MutationOp,
        businessAction: suspend (newHlc: String) -> R
    ): R {
        return database.useWriterConnection { connection ->
            // Start an IMMEDIATE transaction to prevent lock-upgrade deadlocks
            connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                // 1. Generate the pulse (HLC object)
                val hlc = hlcFactory.now()

                // 2. Execute DAO change
                val result = businessAction(hlc.toString())

                // 3. Modern Guard: Logic is now based on Enums, not magic numbers
                val isGhostDelete = op == MutationOp.DELETE && (result as? Int) == 0

                if (!isGhostDelete) {
                    // 4. For Compaction:
                    val pending = ledgerDao.getPendingMutation(entityId, moduleName.toString())

                    // 4. Pruning:
                    val effectiveCreatedAt = if (pending != null) {
                        // If we are compacting a DELETE into an existing DELETE,
                        // we keep the ORIGINAL timestamp to prevent resetting the pruning clock.
                        if (op == MutationOp.DELETE && pending.operation == MutationOp.DELETE) {
                            pending.createdAt
                        } else {
                            // For everything else (Upsert-on-Upsert, Delete-on-Upsert),
                            // this is a fresh intent, so we use the new HLC time.
                            hlc.ts
                        }
                    } else {
                        // New mutation entry
                        hlc.ts
                    }

                    // 5. Compaction Fix: Delete the old record if it exists
                    if (pending != null) {
                        ledgerDao.deleteByHlc(pending.hlc)
                    }

                    // 6. Write the Fresh Intent
                    ledgerDao.upsert(
                        MutationEntryEntity(
                            hlc = hlc,
                            entityId = entityId,
                            entityType = moduleName.toString(),
                            operation = op,
                            syncStatus = SyncStatus.PENDING,
                            createdAt = effectiveCreatedAt
                        )
                    )
                }

                result
            }
        }
    }
}


/**
 * override suspend fun deleteBioEntry(id: String): Int = mutate(
 *     entityId = id,
 *     operationType = MutationOp.DELETE
 * ) {
 *     bioDao.deleteById(id) // Now atomic with the ledger!
 * }
 *
 * override suspend fun saveBioEntry(entry: DailyContext): DailyContext = mutate(
 *     entityId = entry.id,
 *     operationType = MutationOp.UPSERT
 * ) { newHlc ->
 *     val stamped = entry.withHlc(newHlc)
 *     bioDao.upsert(stamped)
 *     stamped
 * }
 */

