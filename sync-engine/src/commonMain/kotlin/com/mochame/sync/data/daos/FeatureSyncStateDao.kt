package com.mochame.sync.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mochame.sync.data.entities.NodeSyncStateEntity

@Dao
interface FeatureSyncStateDao {

    // -----------------------------------------------------------
    // METADATA PROCESSING
    // -----------------------------------------------------------

    @Query("SELECT COUNT(*) FROM NodeSyncStateEntity")
    suspend fun countFeatures(): Int

    @Query("SELECT * FROM NodeSyncStateEntity WHERE feature = :module")
    suspend fun getFeatureMetadata(module: String): NodeSyncStateEntity?

    @Query("SELECT * FROM NodeSyncStateEntity")
    suspend fun getAllMetadata(): List<NodeSyncStateEntity>

    @Upsert
    suspend fun upsertMetadata(metadata: NodeSyncStateEntity)

    // -----------------------------------------------------------
    // HLC FUNCTIONALITY
    // -----------------------------------------------------------

    @Query("SELECT maxHlc FROM NodeSyncStateEntity WHERE feature = :module")
    suspend fun getModuleMaxHlc(module: String): String?

    @Query("SELECT maxHlc FROM NodeSyncStateEntity")
    suspend fun getAllFeatureMaxHlcs(): List<String>

    @Query("SELECT MAX(maxHlc) FROM NodeSyncStateEntity")
    suspend fun getGlobalMaxHlc(): String?

    @Query(
        """
    UPDATE NodeSyncStateEntity 
    SET maxHlc = :newHlcFloor
    WHERE feature = :module 
    AND (maxHlc < :newHlcFloor OR maxHlc IS NULL)
    """
    )
    suspend fun updateHlcFloor(module: String, newHlcFloor: String)

    // -----------------------------------------------------------
    // MAINTENANCE
    // -----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedDefaultMetadata(metadata: List<NodeSyncStateEntity>): List<Long>

    @Transaction
    suspend fun ensureSeeded(expectedModules: List<String>): Int {
        val existingCount = countFeatures()
        if (existingCount >= expectedModules.size) return 0

        val entities = expectedModules.map { module ->
            NodeSyncStateEntity(
                feature = module
            )
        }

        return seedDefaultMetadata(entities).count { it > 0 }
    }

    // -----------------------------------------------------------
    // WATERMARK
    // -----------------------------------------------------------

    @Query(
        """
    UPDATE NodeSyncStateEntity 
    SET serverWatermark = :newWatermark
    WHERE feature = :module
    """
    )
    suspend fun rewindWatermark(
        module: String,
        newWatermark: String?,
    )

    @Query(
        """
        UPDATE NodeSyncStateEntity 
        SET serverWatermark = :watermark, 
            lastServerSyncTime = :timestamp, 
            syncId = NULL
        WHERE feature = :module
    """
    )
    suspend fun stampWatermark(
        module: String,
        watermark: String?,
        timestamp: Long,
    )

}