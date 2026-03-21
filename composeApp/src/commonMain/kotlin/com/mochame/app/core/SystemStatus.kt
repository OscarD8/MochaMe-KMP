package com.mochame.app.core

sealed class SystemStatus {
    object Initializing : SystemStatus()
    object Ready : SystemStatus()
    data class Error(val message: String) : SystemStatus()
}