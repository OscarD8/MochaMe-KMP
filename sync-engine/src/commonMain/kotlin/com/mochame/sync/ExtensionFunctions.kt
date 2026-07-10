package com.mochame.sync

import kotlinx.coroutines.sync.Mutex

/**
 * Executes the block only if the lock is immediately available.
 * Automatically handles the lock release inside a finally block to prevent leaks.
 */
internal inline fun <R> Mutex.tryWithLock(action: () -> R): R? {
    val acquired = tryLock()
    if (!acquired) return null
    try {
        return action()
    } finally {
        unlock()
    }
}