package com.mochame.platform.global

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface GlobalMetadataDao {

    /**
     * Fetch the single source of truth for app-wide identity and state.
     */
    @Query("SELECT * FROM global_settings WHERE id = 1 LIMIT 1")
    suspend fun getGlobalSettings(): GlobalMetadataEntity?

    @Query("SELECT nodeId FROM global_settings WHERE id = 1 LIMIT 1")
    suspend fun getDeviceId(): String?

    /**
     * Atomic transaction for establishing a new node identity, or
     * updating an existing settings config to a new identity.
     */
    @Transaction
    suspend fun updateNodeId(newId: String) {
        val existing = getGlobalSettings()
        val updated = existing?.copy(nodeId = newId)
            ?: GlobalMetadataEntity(id = 1, lastAppVersion = 1, nodeId = newId)
        insert(updated)
    }

    /**
     * Initialize or update the global settings.
     * Using REPLACE ensures that if we call this during a version bump,
     * it just overwrites the existing row 1.
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(settings: GlobalMetadataEntity)

    /**
     * A targeted update for when you just want to bump the version
     * during a Janitor migration check.
     */
    @Query("UPDATE global_settings SET lastAppVersion = :version WHERE id = 1")
    suspend fun updateAppVersion(version: Int)

    /**
     * High-level check to see if we even have an identity yet.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM global_settings WHERE id = 1)")
    suspend fun hasIdentity(): Boolean
}