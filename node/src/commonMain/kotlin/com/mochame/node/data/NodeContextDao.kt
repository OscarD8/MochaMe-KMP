package com.mochame.node.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert


@Dao
interface NodeContextDao {

    @Query("SELECT * FROM node_context WHERE id = 1")
    suspend fun getContext(): NodeContextEntity?

    @Query("SELECT nodeId FROM node_context WHERE id = 1")
    suspend fun getNodeId(): String?

    @Query("SELECT maxHlc FROM node_context WHERE id = 1")
    suspend fun getMaxHlc(): String?

    @Query("SELECT appVersion FROM node_context WHERE id = 1")
    suspend fun getLastBootedVersion(): Int?

    @Query("SELECT lastServerSyncTime FROM node_context WHERE id = 1")
    suspend fun getLastServerSyncTime(): Long?

    @Query("SELECT lastLocalMutationTime FROM node_context WHERE id = 1")
    suspend fun getLastLocalMutationTime(): Long?

    @Query("SELECT lastServerWatermark FROM node_context WHERE id = 1")
    suspend fun getLastWatermark(): Long?

    @Query("UPDATE node_context SET maxHlc = :hlc WHERE id = 1 AND (maxHlc IS NULL OR :hlc > maxHlc)")
    suspend fun setMaxHlc(hlc: String): Int

    @Query("UPDATE node_context SET lastServerWatermark = :watermark, lastServerSyncTime = :timeStamp  WHERE id = 1")
    suspend fun setWatermarkAndTimestamp(watermark: Long, timeStamp: Long)

    @Upsert
    suspend fun upsert(entity: NodeContextEntity)

    @Query("UPDATE node_context SET appVersion = :version WHERE id = 1")
    suspend fun setVersion(version: Int)

    @Transaction
    suspend fun getOrEstablish(
        fallbackId: String,
        baseVersion: Int,
        createdAt: Long
    ): NodeContextEntity {
        return getContext() ?: NodeContextEntity(
            id = 1,
            nodeId = fallbackId,
            appVersion = baseVersion,
            createdAt = createdAt
        ).also { insertOrReplaceContext(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceContext(nodeContext: NodeContextEntity)

}