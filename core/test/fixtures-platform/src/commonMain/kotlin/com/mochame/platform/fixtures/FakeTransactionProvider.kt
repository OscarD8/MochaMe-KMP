package com.mochame.platform.fixtures

import com.mochame.sync.spi.infrastructure.TransactionProvider


/**
 * No database link, just runs the block.
 */
class FakeTransactionProvider : TransactionProvider {

    var executionCount = 0
        private set

    var shouldThrow: Throwable? = null

    override suspend fun <R> runImmediateTransaction(block: suspend () -> R): R {
        executionCount++
        shouldThrow?.let { throw it }
        return block()
    }

    fun reset() {
        executionCount = 0
        shouldThrow = null
    }
}