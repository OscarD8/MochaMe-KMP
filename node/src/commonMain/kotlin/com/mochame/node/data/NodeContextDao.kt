package com.mochame.node.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("UPDATE node_context SET maxHlc = :hlc WHERE id = 1")
    suspend fun setMaxHlc(hlc: String)

    @Query("UPDATE node_context SET lastServerWatermark = :watermark, lastServerSyncTime = :timeStamp  WHERE id = 1")
    suspend fun setWatermarkAndTimestamp(watermark: String, timeStamp: Long)

    @Upsert
    suspend fun upsert(entity: NodeContextEntity)

    @Query("UPDATE node_context SET appVersion = :version WHERE id = 1")
    suspend fun setVersion(version: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceContext(nodeContext: NodeContextEntity)

}