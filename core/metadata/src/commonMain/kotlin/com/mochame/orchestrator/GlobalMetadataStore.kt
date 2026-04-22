package com.mochame.orchestrator

interface GlobalMetadataStore {
    suspend fun getDeviceId() : String?
    suspend fun saveNodeId(newId: String)
}