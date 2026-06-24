package com.mochame.platform.providers

import co.touchlab.kermit.Logger
import com.mochame.contract.providers.BufferProvider
import kotlinx.io.Buffer

class JvmBufferProvider(private val logger: Logger) : BufferProvider {
    private val threadLocal = ThreadLocal.withInitial { Buffer() }
    override fun get(): Buffer {
        val buffer = threadLocal.get()

        val threadName = Thread.currentThread().name
        val threadId = Thread.currentThread().threadId()

        logger.v { "BUFFER | REUSE | Thread: $threadName (ID: $threadId)" }

        buffer.clear()
        return buffer
    }
}