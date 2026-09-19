package com.mochame.server.relay

import co.touchlab.kermit.Logger
import com.mochame.server.config.ServerConfig
import com.mochame.server.database.ServerDatabase
import com.mochame.server.database.runtimeLogPruning
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class RelayServer(
    private val config: ServerConfig = ServerConfig,
    private val database: ServerDatabase,
    private val relayManager: RelayManager,
    private val serverScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val logger: Logger
) : AutoCloseable {

    val databaseActor = DatabaseActor(
        database = database,
        relayManager = relayManager,
        logger = logger,
        scope = serverScope,
        maxBatchSize = config.MAX_BATCH_SIZE
    )

    private var compactorJob: Job? = null
    private var engine: EmbeddedServer<*, *>? = null

    fun start(wait: Boolean = true) {
        compactorJob = serverScope.runtimeLogPruning(
            database = database,
            logger = logger,
            retention = config.LOG_RETENTION_DURATION,
            interval = config.LOG_PRUNE_INTERVAL
        )

        engine = embeddedServer(CIO, port = ServerConfig.PORT, host = ServerConfig.HOST) {
            configureServer(
                database = database,
                relayManager = relayManager,
                databaseActor = databaseActor,
                logger = logger
            )
        }.start(wait = wait)
    }

    override fun close() {
        compactorJob?.cancel()
        serverScope.cancel()
        engine?.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
        database.close()
    }
}