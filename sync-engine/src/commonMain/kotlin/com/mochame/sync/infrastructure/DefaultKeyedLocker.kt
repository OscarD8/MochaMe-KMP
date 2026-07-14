package com.mochame.sync.infrastructure

import com.mochame.sync.spi.infrastructure.KeyedLocker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * A reference-counted locker for keyed synchronization.
 */
@Single(binds = [KeyedLocker::class])
internal class DefaultKeyedLocker : KeyedLocker {
    private class LockEntry(val mutex: Mutex = Mutex(), var activeUsers: Int = 0)

    /**
     * To handle the fact that mutable maps are not thread safe, so the system
     * needs to ensure that no two identical keys establish their own lock at
     * the exact same millisecond. They must have the master lock.
     */
    private val masterLock = Mutex()
    private val keyLocks = mutableMapOf<String, LockEntry>()

    override suspend fun <R> withLock(candidateKey: String, action: suspend () -> R): R {
        val entry = masterLock.withLock {
            keyLocks.getOrPut(candidateKey) { LockEntry() }.apply { activeUsers++ }
        }

        return try {
            entry.mutex.withLock { action() }
        } finally {
            masterLock.withLock {
                entry.activeUsers--
                if (entry.activeUsers == 0) keyLocks.remove(candidateKey)
            }
        }
    }
}