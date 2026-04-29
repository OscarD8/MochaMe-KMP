package com.mochame.system.orchestrator

import co.touchlab.kermit.Logger
import com.mochame.contract.di.IdentityMutex
import com.mochame.contract.di.IoContext
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.contract.identity.GlobalMetadataStore
import com.mochame.contract.identity.IdGenerator
import com.mochame.contract.identity.IdentityManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single(binds = [IdentityManager::class])
class RealIdentityManager(
    @Provided private val metadataStore: GlobalMetadataStore,
    @Provided private val idGenerator: IdGenerator,
    @Provided @IoContext private val ioContext: CoroutineContext,
    @IdentityMutex private val mutex: Mutex,
    @Provided logger: Logger
) : IdentityManager {
    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.METADATA,
        className = "IdentityManager"
    )

    /**
     * Ensures this device has a name before the Janitor starts the clock.
     */
    override suspend fun getOrCreateNodeId(): String = withContext(ioContext) {
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