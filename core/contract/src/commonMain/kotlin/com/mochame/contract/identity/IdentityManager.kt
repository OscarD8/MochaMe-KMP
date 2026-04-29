package com.mochame.contract.identity

interface IdentityManager {
    suspend fun getOrCreateNodeId(): String
}