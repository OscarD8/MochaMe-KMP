package com.mochame.app.data.local.room

import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.domain.sync.MetadataStore
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.infrastructure.sync.HLC

class RoomMetadataStore(private val dao: SyncMetadataDao) : MetadataStore {
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

}