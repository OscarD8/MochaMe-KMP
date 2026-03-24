package com.mochame.app.core

sealed class SystemStatus {
    data object Loading : SystemStatus()
    data object Ready : SystemStatus()
    data class Error(
        val message: String,
        val action: String? = null // e.g., "Adjust Settings" or "Retry"
    ) : SystemStatus()
}