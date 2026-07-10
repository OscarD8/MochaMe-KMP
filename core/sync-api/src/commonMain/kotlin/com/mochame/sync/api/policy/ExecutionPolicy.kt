package com.mochame.sync.api.policy

interface ExecutionPolicy {
    suspend fun <R> execute(
        operationTag: String,
        block: suspend () -> R
    ): R
}