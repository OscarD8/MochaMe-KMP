package com.mochame.sync.spi.infrastructure

/**
 * A reference-counted locker for keyed synchronization.
 */
interface KeyedLocker {
    suspend fun <R> withLock(candidateKey: String, action: suspend () -> R): R
}