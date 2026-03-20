package com.mochame.app.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.app.core.SyncStatus
import com.mochame.app.database.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {

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
    @Query("""
        UPDATE sync_metadata 
        SET syncStatus = :status, activeSyncId = :syncId 
        WHERE moduleName = :moduleName
    """)
    suspend fun updateSyncLock(
        moduleName: String,
        syncId: String?,
        status: SyncStatus = SyncStatus.SYNCING
    )

    /**
     * The Resume Operation: Swapped hardcoded 'IDLE' for a parameter
     * to ensure Enum/TypeConverter compatibility.
     */
    @Query("""
        UPDATE sync_metadata 
        SET serverWatermark = :watermark, 
            lastSyncTime = :timestamp, 
            syncStatus = :status, 
            activeSyncId = NULL
        WHERE moduleName = :moduleName
    """)
    suspend fun finalizeSync(
        moduleName: String,
        watermark: String?,
        timestamp: Long,
        status: SyncStatus = SyncStatus.PENDING // Reconciled from IDLE
    )
}