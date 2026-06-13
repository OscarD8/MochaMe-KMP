package com.mochame.sync.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.data.entities.SyncModuleStateEntity
import com.mochame.sync.domain.state.SyncStatus

@Dao
interface SyncModuleStateDao {

    // -----------------------------------------------------------
    // METADATA BASIC
    // -----------------------------------------------------------

    @Query("SELECT COUNT(*) FROM SyncModuleStateEntity")
    suspend fun getMetadataCount(): Int

    @Query("SELECT * FROM SyncModuleStateEntity WHERE module = :module")
    suspend fun getMetadataForModule(module: MochaModule): SyncModuleStateEntity?

    @Query("SELECT * FROM SyncModuleStateEntity")
    suspend fun getAllMetadata(): List<SyncModuleStateEntity>

    /**
     * The 2026 Way: Atomic update or insert without row destruction.
     */
    @Upsert
    suspend fun upsertMetadata(metadata: SyncModuleStateEntity)

    /**
     * Lightweight Status Check: Avoids loading the entire entity for a quick busy check.
     */
    @Query("SELECT syncStatus FROM SyncModuleStateEntity WHERE module = :module")
    suspend fun getSyncStatus(module: MochaModule): SyncStatus?

    // -----------------------------------------------------------
    // LOCKING
    // -----------------------------------------------------------

    @Query(
        """
    UPDATE SyncModuleStateEntity 
    SET syncId = NULL, syncStatus = :fallbackStatus 
    WHERE module = :module AND syncId = :staleSyncId
    """
    )
    suspend fun releaseLock(
        module: MochaModule,
        staleSyncId: String,
        fallbackStatus: SyncStatus = SyncStatus.PENDING
    ): Int

    /**
     * Only grants a lock if the module is currently IDLE or PENDING.
     * Returns 1 if claim succeeded, 0 if another session already locked it.
     */
    @Query("""
        UPDATE SyncModuleStateEntity 
        SET syncId = :newSyncId, syncStatus = :syncingStatus
        WHERE module = :module 
        AND (syncStatus = :idleStatus OR syncStatus = :pendingStatus)
    """)
    suspend fun claimSyncLock(
        module: MochaModule,
        newSyncId: String,
        idleStatus: SyncStatus = SyncStatus.IDLE,
        pendingStatus: SyncStatus = SyncStatus.PENDING,
        syncingStatus: SyncStatus = SyncStatus.SYNCING
    ): Int



    // -----------------------------------------------------------
    // SYNC STATE TRANSITIONS
    // -----------------------------------------------------------

    @Query(
        """
        UPDATE SyncModuleStateEntity
        SET syncStatus = :toStatus
        WHERE module = :module
        AND syncStatus = :fromStatus
    """
    )
    suspend fun transitionState(
        module: MochaModule,
        toStatus: SyncStatus,
        fromStatus: SyncStatus
    ): Int

    @Query(
        """
    UPDATE SyncModuleStateEntity 
    SET syncStatus = :status, 
        syncId = NULL, 
        serverWatermark = :watermark,
        lastServerSyncTime = :now
    WHERE module = :module 
    AND syncId = :currentSyncId
    """
    )
    suspend fun finalizeSyncSuccess(
        module: MochaModule,
        currentSyncId: String,
        watermark: String,
        now: Long,
        status: SyncStatus = SyncStatus.SUCCESS
    ): Int

    /**
     * Records the local write.
     */
    @Query(
        """
        UPDATE SyncModuleStateEntity 
        SET moduleMaxHlc = :hlc, 
            lastLocalMutationTime = :now ,
            syncStatus = :syncStatus
        WHERE module = :module
    """
    )
    suspend fun recordLocalMutation(
        module: MochaModule,
        hlc: String,
        now: Long,
        syncStatus: SyncStatus
    )

    /**
     * Full Re-Sync Setup: Wipes remote progress but PRESERVES local causality (HLC).
     */
    @Query("""
        UPDATE SyncModuleStateEntity 
        SET serverWatermark = NULL, 
            lastServerSyncTime = 0, 
            syncStatus = :status,
            syncId = NULL
        WHERE module = :module
    """)
    suspend fun resetModuleSyncState(
        module: MochaModule,
        status: SyncStatus = SyncStatus.PENDING
    )

    /**
     * The Resume Operation: Swapped hardcoded 'IDLE' for a parameter
     * to ensure Enum/TypeConverter compatibility.
     */
    @Query(
        """
        UPDATE SyncModuleStateEntity 
        SET serverWatermark = :watermark, 
            lastServerSyncTime = :timestamp, 
            syncStatus = :status, 
            syncId = NULL
        WHERE module = :module
    """
    )
    suspend fun stampMetadata(
        module: MochaModule,
        watermark: String?,
        timestamp: Long,
        status: SyncStatus = SyncStatus.PENDING // Reconciled from IDLE
    )

    // -----------------------------------------------------------
    // HLC
    // -----------------------------------------------------------

    @Query("SELECT moduleMaxHlc FROM SyncModuleStateEntity WHERE module = :module")
    suspend fun getModuleMaxHlc(module: MochaModule): String?

    @Query("SELECT moduleMaxHlc FROM SyncModuleStateEntity")
    suspend fun getAllLocalMaxHlcs(): List<String>

    @Query("SELECT MAX(moduleMaxHlc) FROM SyncModuleStateEntity")
    suspend fun getGlobalMaxHlc(): String?

    @Query(
        """
    UPDATE SyncModuleStateEntity 
    SET moduleMaxHlc = :newHlcFloor
    WHERE module = :module 
    AND (moduleMaxHlc < :newHlcFloor OR moduleMaxHlc IS NULL)
    """
    )
    suspend fun updateHlcFloor(module: MochaModule, newHlcFloor: String): Int


    // -----------------------------------------------------------
    // MAINTENANCE
    // -----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedDefaultMetadata(metadata: List<SyncModuleStateEntity>): List<Long>

    /**
     * Flips any module that isn't 'IDLE' back to 'PENDING'.
     * Returns the number of rows affected so the Janitor can log it.
     */
    @Query(
        """
        UPDATE SyncModuleStateEntity 
        SET syncStatus = :desiredStatus, syncId = NULL 
        WHERE syncStatus != :ignoredStatus
    """
    )
    suspend fun bulkResetDirtyModules(
        desiredStatus: SyncStatus = SyncStatus.PENDING,
        ignoredStatus: SyncStatus = SyncStatus.IDLE
    ): Int

    @Query(
        """
        SELECT module
        FROM SyncModuleStateEntity WHERE syncStatus != :ignoredStatus
    """
    )
    suspend fun getDirtyModuleNames(
        ignoredStatus: SyncStatus = SyncStatus.IDLE
    ): List<String>

    @Transaction
    suspend fun ensureSeeded(expectedModules: List<MochaModule>): Int {
        val existingCount = getMetadataCount()
        if (existingCount >= expectedModules.size) return 0

        val entities = expectedModules.map { module ->
            SyncModuleStateEntity(
                module = module,
                syncStatus = SyncStatus.IDLE
            )
        }

        return seedDefaultMetadata(entities).count { it > 0 }
    }

    // -----------------------------------------------------------
    // WATERMARK
    // -----------------------------------------------------------

    @Query(
        """
    UPDATE SyncModuleStateEntity 
    SET serverWatermark = :newWatermark, 
        syncStatus = :status 
    WHERE module = :module
    """
    )
    suspend fun rewindWatermark(
        module: MochaModule,
        newWatermark: String?,
        status: SyncStatus = SyncStatus.PENDING
    )

}