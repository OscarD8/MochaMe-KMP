package com.mochame.app.data.local.room.dao.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.data.local.room.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedDefaultMetadata(metadata: List<SyncMetadataEntity>)

    @Query("SELECT COUNT(*) FROM sync_metadata")
    suspend fun getMetadataCount(): Int

    @Query("SELECT * FROM sync_metadata WHERE moduleName = :moduleName")
    suspend fun getMetadataForModule(moduleName: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata")
    suspend fun getAllMetadata(): List<SyncMetadataEntity>

    /**
     * The 2026 Way: Atomic update or insert without row destruction.
     */
    @Upsert
    suspend fun upsertMetadata(metadata: SyncMetadataEntity)

    /**
     * Intent-Driven Lock: Room uses the TypeConverter to map the Enum 'status'.
     */
    @Query(
        """
        UPDATE sync_metadata 
        SET syncStatus = :status, syncId = :syncId 
        WHERE moduleName = :moduleName
    """
    )
    suspend fun updateSyncLock(
        moduleName: String,
        syncId: String?,
        status: SyncStatus = SyncStatus.SYNCING
    )

    @Query("SELECT localMaxHlc FROM sync_metadata WHERE moduleName = :moduleName")
    suspend fun getModuleMaxHlc(moduleName: String) : String?

    @Query("SELECT localMaxHlc FROM sync_metadata")
    suspend fun getAllLocalMaxHlcs(): List<String>

    @Query("SELECT MAX(localMaxHlc) FROM sync_metadata")
    suspend fun getGlobalMaxHlc() : String?

    /**
     * Records the heartbeat of a local write.
     * Respects the "Mutation vs Sync" distinction.
     */
    @Query("""
        UPDATE sync_metadata 
        SET localMaxHlc = :hlc, 
            lastLocalMutationTime = :now ,
            syncStatus = :syncStatus
        WHERE moduleName = :moduleName
    """)
    suspend fun recordLocalMutation(moduleName: String, hlc: String, now: Long, syncStatus: SyncStatus)

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
        WHERE moduleName = :moduleName
    """
    )
    suspend fun finalizeSync(
        moduleName: String,
        watermark: String?,
        timestamp: Long,
        status: SyncStatus = SyncStatus.PENDING // Reconciled from IDLE
    )

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

    @Query("""
        SELECT moduleName
        FROM sync_metadata WHERE syncStatus = :ignoredStatus AND syncId = NULL 
    """)
    suspend fun getDirtyModuleNames(
        ignoredStatus: SyncStatus = SyncStatus.IDLE
    ): List<String>
}