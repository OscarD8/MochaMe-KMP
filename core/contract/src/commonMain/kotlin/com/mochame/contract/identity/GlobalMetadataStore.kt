package com.mochame.contract.identity

interface GlobalMetadataStore {
    suspend fun getDeviceId() : String?
    suspend fun saveNodeId(newId: String)
}