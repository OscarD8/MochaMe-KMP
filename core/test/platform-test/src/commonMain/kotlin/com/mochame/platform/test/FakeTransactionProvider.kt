package com.mochame.platform.test

import com.mochame.platform.providers.TransactionProvider

/**
 * No database link, just runs the block.
 */
class FakeTransactionProvider : TransactionProvider {
    override suspend fun <R> runImmediateTransaction(block: suspend () -> R): R {
        return block()
    }
}