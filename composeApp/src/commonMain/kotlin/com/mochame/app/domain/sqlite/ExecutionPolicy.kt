package com.mochame.app.domain.sqlite

interface ExecutionPolicy {
    suspend fun <R> execute(block: suspend () -> R): R
}