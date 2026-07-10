package com.mochame.system.infra.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface NodeContextDao {

    @Query("SELECT * FROM node_identity WHERE id = 1 LIMIT 1")
    suspend fun getContext(): NodeContextEntity?

    @Query("SELECT nodeId FROM node_identity WHERE id = 1 LIMIT 1")
    suspend fun getNodeId(): String?

    @Query("SELECT lastBootedAppVersion FROM node_identity WHERE id = 1")
    suspend fun getLastBootedVersion(): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM node_identity WHERE id = 1)")
    suspend fun hasIdentity(): Boolean

    @Upsert
    suspend fun upsert(entity: NodeContextEntity)

    /**
     * Atomic upsert — preserves existing fields when the row already exists.
     * Baseline version is 0, not 1, so the Janitor can detect a genuine
     * first-boot migration on version 1.
     */
    @Transaction
    suspend fun upsertNodeId(newId: String) {
        val updated = getContext()?.copy(nodeId = newId)
            ?: NodeContextEntity(id = 1, nodeId = newId, lastBootedAppVersion = 0)
        upsert(updated)
    }

    /**
     * Atomic version stamp — pure write, no domain branching.
     * Caller is responsible for ensuring the row exists first.
     */
    @Query("UPDATE node_identity SET lastBootedAppVersion = :version WHERE id = 1")
    suspend fun setVersion(version: Int)

}