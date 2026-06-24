package com.mochame.contract.providers

interface TransactionProvider {
    suspend fun <R> runImmediateTransaction(block: suspend () -> R): R
}