package com.mochame.app.domain.system

import com.benasher44.uuid.uuid4
import com.mochame.app.database.dao.SettingsDao
import com.mochame.app.database.entity.GlobalSettingsEntity
import com.mochame.app.di.DispatcherProvider
import kotlinx.coroutines.withContext

class IdentityManager(
    private val settingsDao: SettingsDao,
    private val dispatcherProvider: DispatcherProvider
) {
    /**
     * Ensures this device has a name before the Janitor starts the clock.
     */
    suspend fun getOrCreateNodeId(): String = withContext(dispatcherProvider.io) {
        val existingSettings = settingsDao.getGlobalSettings()

        if (existingSettings != null) {
            return@withContext existingSettings.nodeId
        }

        val newId = uuid4().toString()

        val initialSettings = GlobalSettingsEntity(
            id = 1, // The fixed Singleton ID
            nodeId = newId,
            lastAppVersion = 1
        )

        settingsDao.insert(initialSettings)
        return@withContext newId
    }
}