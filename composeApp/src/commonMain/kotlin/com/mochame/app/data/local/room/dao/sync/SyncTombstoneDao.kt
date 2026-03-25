package com.mochame.app.data.local.room.dao.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.app.data.local.room.entity.SyncTombstoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncTombstoneDao {
    @Upsert
    suspend fun upsertTombstone(tombstone: SyncTombstoneEntity)

    @Query("SELECT * FROM sync_tombstones WHERE deletedAt > :lastSeenHlc ORDER BY deletedAt ASC")
    fun observeRecentDeletions(lastSeenHlc: String): Flow<List<SyncTombstoneEntity>>

    @Query("SELECT * FROM sync_tombstones WHERE tableName = 'domains' ORDER BY deletedAt ASC")
    suspend fun getAllDomainDeletions(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE candidateKey = :entityId")
    suspend fun clearTombstone(entityId: String)

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :threshold")
    suspend fun pruneOldTombstones(threshold: Long)
}