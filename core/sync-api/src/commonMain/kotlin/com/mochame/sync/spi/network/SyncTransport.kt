package com.mochame.sync.spi.network


interface SyncTransport {
    suspend fun connect(host: String, port: Int, groupId: String, nodeId: String)
    suspend fun send(payload: ByteArray): Boolean
    fun registerInboundHandler(onReceived: suspend (ByteArray) -> Unit)
    fun setOnConnectedListener(onConnected: suspend () -> Unit)
}