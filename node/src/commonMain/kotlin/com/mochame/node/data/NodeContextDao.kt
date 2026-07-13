package com.mochame.node.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert


/**
 * Enforces a single-row constraint (id = 1) via its SQL queries and through the model
 * [NodeContextEntity]. This Dao therefore provides no historical tracking of state, but
 * direct access to the current state with little overhead. Its role is to provide the present
 * context for a single node in the distributed system: information about the nodes identity
 * and synchronization state. It is assumed and enforced by the :sync-api, when defining
 * a node/devices context, it must be coupled with sychronization state that the :sync-engine
 * requires for HLC hydration, and server communication.
 *
 * The Dao does allow for the following scenario:
 * * Thread A (Outbound Sync) generates a mutation and gets a fresh HLC from the factory: HLC(ts=1000, count=1)
 * * Thread B (Inbound Sync) immediately processes a high-frequency server batch, bumping the factory to HLC(ts=1000, count=2)
 * * Thread B completes its execution fast and updates the DAO maxHlc to HLC(ts=1000, count=2)
 * * Thread A suffers a brief CPU context-switch delay. It wakes up and finally executes its suspended database call, blindly calling setMaxHlc("HLC(ts=1000, count=1)")
 *
 * The database has now regressed backward in logical time. If the app process crashes right there, the device boots back up and hydrates its factory using the
 * regressed database state (count=1). If the device wall-clock hasn't advanced, the factory will start re-generating HLCs it already sent to the server in a previous
 * lifecycle, breaking causality and breaking CRDT resolution.
 *
 * The Dao is not responsible for enforcing monotonicity in the above case, it must
 * simply prove it can handle high concurrent writes under multi-threaded stress on each
 * different platform. The manager is responsable for handling the causal logic and interacting
 * with the stateless Dao. I have changed this immediately to making a transaction at the
 * SQLite layer. Never should the maxHlc be persisted to a smaller value, and the monotonicity
 * doesn't matter so much in this model, it matters that it is always the greatest value only.
 */
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

    @Query("UPDATE node_context SET maxHlc = :hlc WHERE id = 1 AND (maxHlc IS NULL OR :hlc > maxHlc)")
    suspend fun setMaxHlc(hlc: String): Int

    @Query("UPDATE node_context SET lastServerWatermark = :watermark, lastServerSyncTime = :timeStamp  WHERE id = 1")
    suspend fun setWatermarkAndTimestamp(watermark: String, timeStamp: Long)

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