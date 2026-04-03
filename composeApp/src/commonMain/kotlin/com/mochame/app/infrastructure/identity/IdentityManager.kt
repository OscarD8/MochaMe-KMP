package com.mochame.app.infrastructure.identity

import co.touchlab.kermit.Logger
import com.benasher44.uuid.uuid4
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.system.settings.SettingsStore
import com.mochame.app.infrastructure.logging.appendTag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class IdentityManager(
    private val settingsStore: SettingsStore,
    private val dispatcherProvider: DispatcherProvider,
    private val mutex: Mutex,
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
        mutex.withLock {
            logger.d { "Resolving Node ID..." }

            val existingId = settingsStore.getDeviceId()
            if (existingId != null) {
                logger.d { "Existing Node ID recovered: $existingId." }
                return@withLock existingId
            }

            // --- New Identity Generation ---
            val newId = uuid4().toString()
            logger.i { "No Node ID found. Generating new identity: $newId" }

            settingsStore.saveNodeId(newId)

            logger.i { "New Node ID successfully persisted to settings." }
            newId
        }
    }
}