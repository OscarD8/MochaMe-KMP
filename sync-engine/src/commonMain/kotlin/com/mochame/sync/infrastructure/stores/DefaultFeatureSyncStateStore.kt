package com.mochame.sync.infrastructure.stores


import com.mochame.sync.contract.FeatureContext
import com.mochame.sync.contract.models.FeatureSyncState
import com.mochame.sync.contract.stores.FeatureSyncStateStore
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.data.daos.FeatureSyncStateDao
import com.mochame.sync.data.toDomain
import com.mochame.sync.domain.stores.FeatureSyncStateMaintenanceStore
import org.koin.core.annotation.Single

@Single(binds = [FeatureSyncStateStore::class, FeatureSyncStateMaintenanceStore::class])
internal class DefaultFeatureSyncStateStore(
    private val dao: FeatureSyncStateDao
) : FeatureSyncStateStore, FeatureSyncStateMaintenanceStore {

    override suspend fun getFeatureMetadata(module: String): FeatureSyncState? =
        dao.getFeatureMetadata(module)?.toDomain()

    override suspend fun updateHlcFloor(module: String, hlc: HLC) =
        dao.updateHlcFloor(module, hlc.toString())

    override suspend fun stampFeatureMetadata(
        module: String,
        watermark: String?,
        timestamp: Long
    ) = dao.stampWatermark(module, watermark, timestamp)

    // -----------------------------------------------------------
    // MAINTENANCE
    // -----------------------------------------------------------

    override suspend fun getGlobalMaxHlc(): String? = dao.getGlobalMaxHlc()

    override suspend fun ensureSeeded(): Int =
        dao.ensureSeeded(FeatureContext.allFeatureModules)

    override suspend fun getFeatureCount(): Int = dao.countFeatures()

}