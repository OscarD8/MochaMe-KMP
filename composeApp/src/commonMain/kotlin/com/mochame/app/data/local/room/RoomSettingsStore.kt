package com.mochame.app.data.local.room

import com.mochame.app.data.local.room.dao.SettingsDao
import com.mochame.app.data.local.room.entity.GlobalSettingsEntity
import com.mochame.app.domain.system.settings.SettingsStore

class RoomSettingsStore(private val dao: SettingsDao) : SettingsStore {
    override suspend fun saveNodeId(newId: String) {
        val existing = dao.getGlobalSettings()

        val newSettings = existing?.copy(
            nodeId = newId
        ) ?: GlobalSettingsEntity(
            id = 1,
            nodeId = newId,
            lastAppVersion = 1,
        )

        dao.insert(newSettings)
    }

    override suspend fun getDeviceId(): String? = dao.getDeviceId()
}