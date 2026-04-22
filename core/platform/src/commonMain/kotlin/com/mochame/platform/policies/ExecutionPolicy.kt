package com.mochame.platform.policies

interface ExecutionPolicy {
    suspend fun <R> execute(
        operationTag: String,
        block: suspend () -> R
    ): R
}