package com.mochame.platform.providers

sealed interface DatabaseLocation {
    data object InMemory : DatabaseLocation
    data class OnDisk(val path: String) : DatabaseLocation
}