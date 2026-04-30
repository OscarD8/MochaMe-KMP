package com.mochame.contract.node

interface NodeContextManager {
    suspend fun getOrCreateNodeId(): String
    suspend fun initializeContext(currentVersion: Int): NodeContext
}