package com.mochame.app.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.mochame.app.database.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE moduleName = :moduleName")
    suspend fun getMetadata(moduleName: String): SyncMetadataEntity?

    @Query("UPDATE sync_metadata SET lastWatermark = :watermark, activeSessionId = NULL, lastSyncStatus = 0, lastSyncTime = :timestamp WHERE moduleName = :moduleName")
    suspend fun updateWatermark(moduleName: String, watermark: String, timestamp: Long)

    @Query("UPDATE sync_metadata SET activeSessionId = :sessionId, lastSyncStatus = 1 WHERE moduleName = :moduleName")
    suspend fun beginSession(moduleName: String, sessionId: String)

    @Query("UPDATE sync_metadata SET activeSessionId = NULL, lastSyncStatus = :status WHERE moduleName = :moduleName")
    suspend fun endSession(moduleName: String, status: Int)

    // THE JANITOR: Finds sessions that are "In-Progress" but have no active worker
    @Query("SELECT * FROM sync_metadata WHERE activeSessionId IS NOT NULL")
    suspend fun getInProgressSessions(): List<SyncMetadataEntity>
}