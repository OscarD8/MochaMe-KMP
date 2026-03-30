package com.mochame.app.domain.system.settings

interface SettingsStore {
    suspend fun getDeviceId() : String?
    suspend fun saveNodeId(newId: String)
}