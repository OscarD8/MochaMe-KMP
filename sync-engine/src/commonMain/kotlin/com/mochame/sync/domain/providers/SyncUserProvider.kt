package com.mochame.sync.domain.providers

interface SyncUserProvider {
    /** Returns the unique identifier for this node.*/
    suspend fun getOrCreateNodeId(): String
}