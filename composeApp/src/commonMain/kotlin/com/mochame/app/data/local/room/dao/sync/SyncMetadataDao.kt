package com.mochame.app.data.local.room.dao.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mochame.app.domain.system.sync.utils.SyncStatus
import com.mochame.app.data.local.room.entity.SyncMetadataEntity
import com.mochame.app.domain.system.sync.utils.MochaModule

@Dao
interface SyncMetadataDao {

    // -----------------------------------------------------------
    // METADATA BASIC
    // -----------------------------------------------------------

    @Query("SELECT COUNT(*) FROM sync_metadata")
    suspend fun getMetadataCount(): Int

    @Query("SELECT * FROM sync_metadata WHERE module = :module")
    suspend fun getMetadataForModule(module: MochaModule): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata")
    suspend fun getAllMetadata(): List<SyncMetadataEntity>

    /**
     * The 2026 Way: Atomic update or insert without row destruction.
     */
    @Upsert
    suspend fun upsertMetadata(metadata: SyncMetadataEntity)

    /**
     * Lightweight Status Check: Avoids loading the entire entity for a quick busy check.
     */
    @Query("SELECT syncStatus FROM sync_metadata WHERE module = :module")
    suspend fun getSyncStatus(module: MochaModule): SyncStatus?

    // -----------------------------------------------------------
    // LOCKING
    // -----------------------------------------------------------

    @Query(
        """
    UPDATE sync_metadata 
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
        UPDATE sync_metadata 
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
        UPDATE sync_metadata
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
    UPDATE sync_metadata 
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
        UPDATE sync_metadata 
        SET localMaxHlc = :hlc, 
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
        UPDATE sync_metadata 
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
        UPDATE sync_metadata 
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

    @Query("SELECT localMaxHlc FROM sync_metadata WHERE module = :module")
    suspend fun getModuleMaxHlc(module: MochaModule): String?

    @Query("SELECT localMaxHlc FROM sync_metadata")
    suspend fun getAllLocalMaxHlcs(): List<String>

    @Query("SELECT MAX(localMaxHlc) FROM sync_metadata")
    suspend fun getGlobalMaxHlc(): String?

    @Query(
        """
    UPDATE sync_metadata 
    SET localMaxHlc = :newHlcFloor
    WHERE module = :module 
    AND (localMaxHlc < :newHlcFloor OR localMaxHlc IS NULL)
    """
    )
    suspend fun updateHlcFloor(module: MochaModule, newHlcFloor: String): Int


    // -----------------------------------------------------------
    // MAINTENANCE
    // -----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedDefaultMetadata(metadata: List<SyncMetadataEntity>): List<Long>

    /**
     * Flips any module that isn't 'IDLE' back to 'PENDING'.
     * Returns the number of rows affected so the Janitor can log it.
     */
    @Query(
        """
        UPDATE sync_metadata 
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
        FROM sync_metadata WHERE syncStatus != :ignoredStatus
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
            SyncMetadataEntity(
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
    UPDATE sync_metadata 
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