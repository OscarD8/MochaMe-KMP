package com.mochame.app.domain.sync

interface TransactionProvider {
    suspend fun <R> runImmediateTransaction(block: suspend () -> R): R
}