package com.mochame.app.data.local.room

import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
import com.mochame.app.data.local.room.entity.SyncMetadataEntity
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.domain.sync.MetadataStore
import com.mochame.app.domain.sync.MetadataStoreMaintenance
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.infrastructure.sync.HLC

class RoomMetadataStore(private val dao: SyncMetadataDao)
    : MetadataStore, MetadataStoreMaintenance
{
    override suspend fun recordMetadata(
        moduleName: MochaModule,
        hlc: HLC
    ) {
        dao.recordLocalMutation(
            moduleName = moduleName.tag, // Using the tag for DB identity
            hlc = hlc.toString(),
            now = hlc.ts,
            syncStatus = SyncStatus.PENDING
        )
    }

    override suspend fun bulkResetDirtyModules(): Int {
        return dao.bulkResetDirtyModules()
    }

    override suspend fun getDirtyModuleNames(): List<String> {
        return dao.getDirtyModuleNames()
    }

    override suspend fun getMetadataCount(): Int {
        return dao.getMetadataCount()
    }

    override suspend fun ensureSeeded(): Int {
        val expected = MochaModule.entries
        val existingCount = dao.getMetadataCount()

        if (existingCount < expected.size) {
            val entities = expected.map { module ->
                SyncMetadataEntity(
                    moduleName = module,
                    syncStatus = SyncStatus.IDLE
                )
            }
            val results = dao.seedDefaultMetadata(entities)
            return results.count { it > 0 }
        }
        return 0
    }

    override suspend fun getGlobalMaxHlc(): String? {
        return dao.getGlobalMaxHlc()
    }

}