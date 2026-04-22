package com.mochame.sync.infrastructure

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * A KMP-compliant, reference-counted locker for keyed synchronization.
 */
@Single
class KeyedLocker {
    private class LockEntry(val mutex: Mutex = Mutex(), var activeUsers: Int = 0)

    /**
     * To handle the fact that mutable maps are not thread safe, so the system
     * needs to ensure that no two identical keys establish their own lock at
     * the exact same millisecond. They must have the master lock.
     */
    private val masterLock = Mutex()
    private val keyLocks = mutableMapOf<String, LockEntry>()

    suspend fun <R> withLock(key: String, action: suspend () -> R): R {
        val entry = masterLock.withLock {
            keyLocks.getOrPut(key) { LockEntry() }.apply { activeUsers++ }
        }

        return try {
            entry.mutex.withLock { action() }
        } finally {
            masterLock.withLock {
                entry.activeUsers--
                if (entry.activeUsers == 0) keyLocks.remove(key)
            }
        }
    }
}