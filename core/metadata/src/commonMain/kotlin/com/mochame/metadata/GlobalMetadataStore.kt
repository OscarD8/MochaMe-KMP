package com.mochame.metadata

interface GlobalMetadataStore {
    suspend fun getDeviceId() : String?
    suspend fun saveNodeId(newId: String)
}