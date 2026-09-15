package com.mochame.platform.providers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

class AppBackgroundScopeOwner(
    context: CoroutineContext
) : AutoCloseable, CoroutineScope by CoroutineScope(context + SupervisorJob()) {

    init {
        println("Created background scope: $this")
    }

    override fun close() {
        println("Closing background scope: $this")
        cancel()
    }

    override fun toString(): String = "AppBackgroundScopeOwner($coroutineContext)"
}
