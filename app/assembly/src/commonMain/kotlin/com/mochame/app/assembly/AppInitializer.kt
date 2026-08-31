package com.mochame.app.assembly

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.spi.boot.BootStatusUpdater
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import com.mochame.sync.spi.orchestration.SyncCoordinator
import com.mochame.sync.spi.orchestration.SyncJanitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

interface AppInitializer {
    fun startBootSequence()
}

@Single(binds = [AppInitializer::class], createdAtStart = true)
internal class DefaultAppInitializer(
    @Provided private val janitor: SyncJanitor,
    @Provided private val coordinator: SyncCoordinator,
    @Provided private val bootUpdater: BootStatusUpdater,
    @Provided private val workerHook: SyncWorkerHook,
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

                bootUpdater.updateState(BootState.Ready)
                logger.i { "Application initialized successfully..." }

                coordinator.startOutbound()

            } catch (e: Exception) {
                logger.e(e) { "Boot sequence encountered a critical failure." }
                bootUpdater.updateState(BootState.CriticalFailure(e.message ?: "Unknown error", e))
            }
        }
    }
}