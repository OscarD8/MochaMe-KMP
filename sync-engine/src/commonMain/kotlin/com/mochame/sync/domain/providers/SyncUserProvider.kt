package com.mochame.sync.domain.providers

internal interface SyncUserProvider {
    /** Returns the unique identifier for this node.*/
    suspend fun getOrCreateNodeId(): String
}