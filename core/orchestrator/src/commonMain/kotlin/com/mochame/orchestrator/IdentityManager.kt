package com.mochame.orchestrator

import co.touchlab.kermit.Logger
import com.mochame.di.IdentityMutex
import com.mochame.utils.IdGenerator
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single
class IdentityManager(
    private val metadataStore: GlobalMetadataStore,
    private val workContext: CoroutineContext,
    private val idGenerator: IdGenerator,
    @IdentityMutex private val mutex: Mutex,
    logger: Logger
) {
    private val logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.METADATA,
        className = "IdentityManager"
    )

    /**
     * Ensures this device has a name before the Janitor starts the clock.
     */
    suspend fun getOrCreateNodeId(): String = withContext(workContext) {
        mutex.withLock {
            logger.d { "Resolving Node ID..." }

            val existingId = metadataStore.getDeviceId()
            if (existingId != null) {
                logger.d { "Existing Node ID recovered: $existingId." }
                return@withLock existingId
            }

            // --- New Identity Generation ---
            val newId = idGenerator.nextId()
            logger.i { "No Node ID found. Generating new identity: $newId" }

            metadataStore.saveNodeId(newId)

            logger.i { "New Node ID successfully persisted to settings." }
            newId
        }
    }
}