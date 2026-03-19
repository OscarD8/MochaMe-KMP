package com.mochame.app.core

enum class SyncStatus(val value: Int) {
    SYNCED(0),   // Vault and Client are in harmony
    PENDING(1),  // Dirty/User Edited; needs to upload
    SYNCING(2);   // In-Flight; currently being handled by SyncCoordinator

    companion object {
        // This is the method the mapper was looking for
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: PENDING
    }
}