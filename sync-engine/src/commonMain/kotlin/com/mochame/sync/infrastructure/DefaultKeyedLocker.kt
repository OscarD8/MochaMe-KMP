package com.mochame.sync.infrastructure

import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.domain.model.LockKey
import com.mochame.sync.spi.infrastructure.KeyedLocker
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val registryLock = reentrantLock()
    private val keyLocks = mutableMapOf<LockKey, LockEntry>()

    override suspend fun <R> withLock(
        context: FeatureContext,
        candidateKey: Long,
        action: suspend () -> R
    ): R {
        val key = LockKey(context, candidateKey)
        val entry = registryLock.withLock {
            keyLocks.getOrPut(key) { LockEntry() }.apply { activeUsers++ }
        }

        return try {
            entry.mutex.withLock { action() }
        } finally {
            withContext(NonCancellable) {
                registryLock.withLock {
                    entry.activeUsers--
                    if (entry.activeUsers == 0) {
                        keyLocks.remove(key)
                    }
                }
            }
        }
    }
}