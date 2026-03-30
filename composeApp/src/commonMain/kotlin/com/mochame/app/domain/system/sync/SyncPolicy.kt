package com.mochame.app.domain.system.sync

interface SyncPolicy<T> {
    /**
     * Determines which version of the entity takes priority.
     * Usually follows Last-Write-Wins (LWW) using HLC or lastModified.
     */
    fun resolveConflict(local: T, remote: T): T

    /**
     * Extracts the unique identity of the record for Tombstone lookups.
     */
    fun getIdentity(entity: T): String

    /**
     * Returns the timestamp used for causal ordering.
     */
    fun getVersion(entity: T): Long
}