package com.mochame.app.domain.system.sqlite

interface ExecutionPolicy {
    suspend fun <R> execute(block: suspend () -> R): R
}