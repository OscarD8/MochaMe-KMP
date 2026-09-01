package com.mochame.app.assembly

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.domain.model.SyncConfig
import com.mochame.sync.spi.boot.BootStatusUpdater
import com.mochame.sync.spi.network.SyncTransport
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.orchestration.SyncCoordinator
import com.mochame.sync.spi.orchestration.SyncJanitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds

interface AppInitializer {
    fun startBootSequence()
}

@Single(binds = [AppInitializer::class], createdAtStart = true)
internal class DefaultAppInitializer(
    @Provided private val janitor: SyncJanitor,
    @Provided private val coordinator: SyncCoordinator,
    @Provided private val bootUpdater: BootStatusUpdater,
    @Provided private val nodeContextManager: NodeContextManager,
    @Provided private val transport: SyncTransport,
    @Provided private val syncConfig: SyncConfig,
    @AppBackgroundScope private val appBackgroundScope: CoroutineScope,
    logger: Logger
) : AppInitializer {

    private val logger =
        logger.withTags(LogTags.Layer.ORCH, LogTags.Domain.BOOT, "AppIni")

    init {
        startBootSequence()
    }

    override fun startBootSequence() {
        appBackgroundScope.launch {
            bootUpdater.updateState(BootState.Init)

            try {
                logger.i { "Initializing application..." }

                janitor.startupChecks().join()

                val nodeId = nodeContextManager.getNodeId()
                    ?: error("Failed to retrieve node ID: NodeContext is not initialized")

                bootUpdater.updateState(BootState.Ready)
                logger.i { "Application initialized successfully..." }

                transport.registerInboundHandler { bytes ->
                    coordinator.onInboundBytes(bytes)
                }

                transport.setOnConnectedListener {
                    logger.i { "Transport connection established..." }
                    coordinator.processQueueUntilExhausted()
                }

                transport.connect(
                    host = syncConfig.serverHost,
                    port = syncConfig.serverPort,
                    groupId = syncConfig.syncGroupId,
                    nodeId = nodeId.value.toString()
                )

                delay(3.seconds)

                coordinator.startOutbound()

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                logger.e(e) { "Boot sequence encountered a critical failure." }
                bootUpdater.updateState(BootState.CriticalFailure(e.message ?: "Unknown error", e))
            }
        }
    }
}