package com.mochame.app.domain.system.sync

interface TransactionProvider {
    suspend fun <R> runImmediateTransaction(block: suspend () -> R): R
}