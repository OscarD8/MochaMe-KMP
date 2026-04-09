package com.mochame.app.data.local.room

import com.mochame.app.data.local.room.dao.SettingsDao
import com.mochame.app.domain.system.settings.SettingsStore
import com.mochame.app.domain.system.sqlite.ExecutionPolicy

class RoomSettingsStore(
    private val dao: SettingsDao,
) : SettingsStore {
    override suspend fun saveNodeId(newId: String)  {
        dao.updateNodeId(newId)
    }

    override suspend fun getDeviceId(): String? =  dao.getDeviceId()
}