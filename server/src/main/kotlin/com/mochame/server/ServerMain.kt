package com.mochame.server

import com.mochame.server.config.ServerConfig
import com.mochame.server.config.ServerLogger
import com.mochame.server.database.ServerDatabase
import com.mochame.server.database.dbPath
import com.mochame.server.relay.RelayManager
import com.mochame.server.relay.RelayServer


fun main() {
    val logger = ServerLogger.base.withTag("RelaySvr")

    val resolvedDbPath = System.getenv("MOCHAME_DB_PATH") ?: dbPath
    val database = ServerDatabase(resolvedDbPath)
    val relayManager = RelayManager(logger)

    val server = RelayServer(
        database = database,
        relayManager = relayManager,
        logger = logger
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.i { "Shutdown signal received. Closing Sync Relay Server..." }
        server.close()
    })

    logger.i { "Starting Relay Server on ${ServerConfig.HOST}:${ServerConfig.PORT} (DB: $resolvedDbPath)" }
    server.start(wait = true)
}