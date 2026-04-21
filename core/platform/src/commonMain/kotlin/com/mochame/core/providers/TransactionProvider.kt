package com.mochame.core.providers

interface TransactionProvider {
    suspend fun <R> runImmediateTransaction(block: suspend () -> R): R
}