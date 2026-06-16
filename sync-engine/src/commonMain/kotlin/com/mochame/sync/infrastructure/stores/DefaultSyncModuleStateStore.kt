package com.mochame.sync.infrastructure.stores


import com.mochame.contract.metadata.MochaModule
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

    suspend fun getModuleMetadata(module: MochaModule): SyncModuleStateEntity? {
        return dao.getMetadataForModule(module)
    }

    override suspend fun ensureSeeded(): Int {
        return dao.ensureSeeded(MochaModule.all)
    }

    override suspend fun getGlobalMaxHlc(): String? {
        return dao.getGlobalMaxHlc()
    }

    override suspend fun updateHlcFloor(module: MochaModule, hlc: HLC) =
        dao.updateHlcFloor(module, hlc.toString())

//    override suspend fun stampWatermark(
//        module: MochaModule,
//        syncId: String,
//        newWatermark: String
//    ) {
//        dao.stampMetadata(module,syncId,newWatermark)
//    }

}