package com.mochame.app.domain.sync

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.mochame.app.core.SyncStatus
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.MutationLedgerDao
import com.mochame.app.database.dao.SyncMetadataDao

class SyncJanitor(
    private val database: MochaDatabase,
    private val metadataDao: SyncMetadataDao,
    private val ledgerDao: MutationLedgerDao
) {
    /**
     * Recovery Protocol.
     */
    suspend fun performCleanBoot() {
        database.useWriterConnection { connection ->
            // Use IMMEDIATE to kick out any lingering background readers/writers
            connection.withTransaction(type = Transactor.SQLiteTransactionType.IMMEDIATE) {
                val allModules = metadataDao.getAllMetadata()

                allModules.forEach { meta ->
                    when (meta.syncStatus) {
                        SyncStatus.SYNCING -> {
                            // Recover the specific rows 'stuck' in the crash
                            meta.activeSyncId?.let { staleId ->
                                ledgerDao.unlockOrphanedRecords(meta.moduleName, staleId)
                            }

                            // Reset the module to IDLE
                            metadataDao.upsertMetadata(meta.copy(
                                syncStatus = SyncStatus.PENDING,
                                activeSyncId = null
                            ))
                        }

                        SyncStatus.PENDING -> {
                            // THE SAFETY CATCH: Clear any rows that leaked
                            // before the Metadata could record 'SYNCING'
                            ledgerDao.clearAllStaleLocks(meta.moduleName)
                        }
                        else -> { /* FAILED/SUCCESS are already stable states */ }
                    }
                }
            }
        }
    }
}