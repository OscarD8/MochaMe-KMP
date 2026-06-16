package com.mochame.sync.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.data.entities.SyncModuleStateEntity

@Dao
interface SyncModuleStateDao {

    // -----------------------------------------------------------
    // METADATA BASIC
    // -----------------------------------------------------------

    @Query("SELECT COUNT(*) FROM SyncModuleStateEntity")
    suspend fun getMetadataCount(): Int

    @Query("SELECT * FROM SyncModuleStateEntity WHERE module = :module")
    suspend fun getMetadataForModule(module: MochaModule): SyncModuleStateEntity?

    @Query("SELECT * FROM SyncModuleStateEntity")
    suspend fun getAllMetadata(): List<SyncModuleStateEntity>

    /**
     * The 2026 Way: Atomic update or insert without row destruction.
     */
    @Upsert
    suspend fun upsertMetadata(metadata: SyncModuleStateEntity)

    @Query(
        """
        UPDATE SyncModuleStateEntity 
        SET serverWatermark = :watermark, 
            lastServerSyncTime = :timestamp, 
            syncId = NULL
        WHERE module = :module
    """
    )
    suspend fun stampMetadata(
        module: MochaModule,
        watermark: String?,
        timestamp: Long,
    )

    // -----------------------------------------------------------
    // HLC
    // -----------------------------------------------------------

    @Query("SELECT moduleMaxHlc FROM SyncModuleStateEntity WHERE module = :module")
    suspend fun getModuleMaxHlc(module: MochaModule): String?

    @Query("SELECT moduleMaxHlc FROM SyncModuleStateEntity")
    suspend fun getAllLocalMaxHlcs(): List<String>

    @Query("SELECT MAX(moduleMaxHlc) FROM SyncModuleStateEntity")
    suspend fun getGlobalMaxHlc(): String?

    @Query(
        """
    UPDATE SyncModuleStateEntity 
    SET moduleMaxHlc = :newHlcFloor
    WHERE module = :module 
    AND (moduleMaxHlc < :newHlcFloor OR moduleMaxHlc IS NULL)
    """
    )
    suspend fun updateHlcFloor(module: MochaModule, newHlcFloor: String)


    // -----------------------------------------------------------
    // MAINTENANCE
    // -----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedDefaultMetadata(metadata: List<SyncModuleStateEntity>): List<Long>

    @Transaction
    suspend fun ensureSeeded(expectedModules: List<MochaModule>): Int {
        val existingCount = getMetadataCount()
        if (existingCount >= expectedModules.size) return 0

        val entities = expectedModules.map { module ->
            SyncModuleStateEntity(
                module = module
            )
        }

        return seedDefaultMetadata(entities).count { it > 0 }
    }

    // -----------------------------------------------------------
    // WATERMARK
    // -----------------------------------------------------------

    @Query(
        """
    UPDATE SyncModuleStateEntity 
    SET serverWatermark = :newWatermark
    WHERE module = :module
    """
    )
    suspend fun rewindWatermark(
        module: MochaModule,
        newWatermark: String?,
    )

}