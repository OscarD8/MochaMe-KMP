package com.mochame.app.domain.system.sqlite

interface ExecutionPolicy {
    suspend fun <R> execute(
        operationTag: String,
        block: suspend () -> R
    ): R
}