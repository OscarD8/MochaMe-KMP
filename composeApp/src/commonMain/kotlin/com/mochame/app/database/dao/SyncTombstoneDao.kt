package com.mochame.app.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.app.database.entity.SyncTombstoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncTombstoneDao {
    @Upsert
    suspend fun upsertTombstone(tombstone: SyncTombstoneEntity)

    @Query("SELECT * FROM sync_tombstones WHERE deletedAt > :since ORDER BY deletedAt ASC")
    fun observeRecentDeletions(since: Long): Flow<List<SyncTombstoneEntity>>

    @Query("SELECT * FROM sync_tombstones WHERE tableName = 'domains' ORDER BY deletedAt ASC")
    suspend fun getAllDomainDeletions(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE entityId = :entityId")
    suspend fun clearTombstone(entityId: String)

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :threshold")
    suspend fun pruneOldTombstones(threshold: Long)
}