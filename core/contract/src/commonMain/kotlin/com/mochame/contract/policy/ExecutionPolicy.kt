package com.mochame.contract.policy

interface ExecutionPolicy {
    suspend fun <R> execute(
        operationTag: String,
        block: suspend () -> R
    ): R
}