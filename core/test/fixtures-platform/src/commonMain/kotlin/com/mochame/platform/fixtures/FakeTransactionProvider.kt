package com.mochame.platform.fixtures

import com.mochame.sync.spi.infrastructure.TransactionProvider


/**
 * No database link, just runs the block.
 */
class FakeTransactionProvider : TransactionProvider {
    override suspend fun <R> runImmediateTransaction(block: suspend () -> R): R {
        return block()
    }
}