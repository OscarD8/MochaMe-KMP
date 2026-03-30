package com.mochame.app.data.local.room

import com.mochame.app.domain.system.settings.SettingsStore
import kotlinx.coroutines.yield

class FakeSettingsStore : SettingsStore {
    var storedId: String? = null
    override suspend fun getDeviceId(): String? {
        return storedId
    }
    override suspend fun saveNodeId(newId: String) {
        storedId = newId
    }
}