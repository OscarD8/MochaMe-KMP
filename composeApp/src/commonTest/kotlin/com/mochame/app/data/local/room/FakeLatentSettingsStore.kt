package com.mochame.app.data.local.room

import com.mochame.app.domain.system.settings.SettingsStore
import kotlinx.coroutines.yield

class FakeLatentSettingsStore : SettingsStore {
    var storedId: String? = null
    override suspend fun getDeviceId(): String? {
        yield()
        return storedId
    }
    override suspend fun saveNodeId(newId: String) {
        yield()
        storedId = newId
    }
}