package com.mochame.platform.providers

interface TransactionProvider {
    suspend fun <R> runImmediateTransaction(block: suspend () -> R): R
}