package com.mochame.core.policies

interface ExecutionPolicy {
    suspend fun <R> execute(
        operationTag: String,
        block: suspend () -> R
    ): R
}