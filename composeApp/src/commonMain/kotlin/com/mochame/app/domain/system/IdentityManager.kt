package com.mochame.app.domain.system

import co.touchlab.kermit.Logger
import com.benasher44.uuid.uuid4
import com.mochame.app.database.dao.SettingsDao
import com.mochame.app.database.entity.GlobalSettingsEntity
import com.mochame.app.di.DispatcherProvider
import kotlinx.coroutines.withContext

class IdentityManager(
    private val settingsDao: SettingsDao,
    private val dispatcherProvider: DispatcherProvider,
    private val logger: Logger
) {
    /**
     * Ensures this device has a name before the Janitor starts the clock.
     */
    suspend fun getOrCreateNodeId(): String = withContext(dispatcherProvider.io) {
        runCatching {
            logger.d { "IdentityManager: Resolving Node ID..." }

            val existingId = settingsDao.getDeviceId()

            if (existingId != null) {
                logger.d { "IdentityManager: Existing Node ID recovered: $existingId." }
                return@runCatching existingId
            }

            // --- Milestone: New Identity Generation ---
            val newId = uuid4().toString()
            logger.i { "IdentityManager: No Node ID found. Generating new identity: $newId" }

            val initialSettings = GlobalSettingsEntity(
                id = 1,
                nodeId = newId,
                lastAppVersion = 1
            )

            settingsDao.insert(initialSettings)

            logger.i { "IdentityManager: New Node ID successfully persisted to settings." }
            newId
        }.onFailure { e ->
            logger.e(e) { "IdentityManager: failure during Node ID resolution." }
        }.getOrThrow()
    }
}