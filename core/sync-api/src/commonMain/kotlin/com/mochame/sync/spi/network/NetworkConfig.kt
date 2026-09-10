package com.mochame.sync.spi.network

data class NetworkConfig(
    val serverHost: String = "127.0.0.1",
    val serverPort: Int = 8080,
    val syncGroupId: String = "sync-group-dev-001"
)