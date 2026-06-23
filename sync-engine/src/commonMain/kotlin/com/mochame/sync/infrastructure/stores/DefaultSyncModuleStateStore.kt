package com.mochame.sync.infrastructure.stores


import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.sync.contract.HLC
import com.mochame.sync.data.daos.SyncModuleStateDao
import com.mochame.sync.data.entities.SyncModuleStateEntity
import com.mochame.sync.domain.stores.SyncModuleStateStore
import com.mochame.sync.domain.stores.SyncModuleStateMaintenanceStore
import org.koin.core.annotation.Single

@Single(binds = [SyncModuleStateStore::class, SyncModuleStateMaintenanceStore::class])
class DefaultSyncModuleStateStore(
    private val dao: SyncModuleStateDao
) : SyncModuleStateStore, SyncModuleStateMaintenanceStore {


    override suspend fun getMetadataCount(): Int {
        return dao.getMetadataCount()
    }

    suspend fun getModuleMetadata(module: String): SyncModuleStateEntity? {
        return dao.getMetadataForModule(module)
    }

    override suspend fun ensureSeeded(): Int {
        return dao.ensureSeeded(MochaModuleContext.allFeatureModules)
    }

    override suspend fun getGlobalMaxHlc(): String? {
        return dao.getGlobalMaxHlc()
    }

    override suspend fun updateHlcFloor(module: String, hlc: HLC) =
        dao.updateHlcFloor(module, hlc.toString())

    override suspend fun stampModuleMetadata(
        module: String,
        watermark: String?,
        timestamp: Long
    ) {
        dao.stampMetadata(module,watermark,timestamp)
    }

}