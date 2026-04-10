package com.mochame.app.infrastructure.utils

import co.touchlab.kermit.Logger
import kotlinx.io.Buffer

class AndroidBufferProvider(private val logger: Logger): BufferProvider {
    private val threadLocal = ThreadLocal.withInitial { Buffer() }

    override fun get(): Buffer {
        val buffer = threadLocal.get() ?: Buffer().also { threadLocal.set(it) }

        // --- The JVM way to see the "Who" ---
        val threadName = Thread.currentThread().name

        logger.v { "BUFFER | REUSE | Thread: $threadName" }

        buffer.clear()
        return buffer
    }
}