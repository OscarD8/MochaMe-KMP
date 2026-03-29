package com.mochame.app.infrastructure.identity

import co.touchlab.kermit.Logger
import com.benasher44.uuid.uuid4
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.data.local.room.dao.SettingsDao
import com.mochame.app.data.local.room.entity.GlobalSettingsEntity
import com.mochame.app.di.providers.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class IdentityManager(
    private val settingsDao: SettingsDao,
    private val dispatcherProvider: DispatcherProvider,
    logger: Logger
) {
    companion object {
        private const val TAG = "Identity"
    }
    private val logger = logger.appendTag(TAG)

    /**
     * Ensures this device has a name before the Janitor starts the clock.
     */
    suspend fun getOrCreateNodeId(): String = withContext(dispatcherProvider.io) {
        try {
            logger.d { "Resolving Node ID..." }

            val existingId = settingsDao.getDeviceId()

            if (existingId != null) {
                logger.d { "Existing Node ID recovered: $existingId." }
                return@withContext existingId
            }

            // --- New Identity Generation ---
            val newId = uuid4().toString()
            logger.i { "No Node ID found. Generating new identity: $newId" }

            val initialSettings = GlobalSettingsEntity(
                id = 1,
                nodeId = newId,
                lastAppVersion = 1
            )

            settingsDao.insert(initialSettings)

            logger.i { "New Node ID successfully persisted to settings." }
            newId
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            logger.e(e) { "Failure during Node ID resolution." }
            throw e
        }
    }
}