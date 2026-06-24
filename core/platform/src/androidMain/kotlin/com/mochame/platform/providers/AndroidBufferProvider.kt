package com.mochame.platform.providers

import co.touchlab.kermit.Logger
import com.mochame.contract.providers.BufferProvider
import kotlinx.io.Buffer
import org.koin.core.annotation.Single

@Single(binds = [BufferProvider::class])
class AndroidBufferProvider(private val logger: Logger): BufferProvider {
    private val threadLocal = ThreadLocal.withInitial { Buffer() }

    override fun get(): Buffer {
        val buffer = threadLocal.get() ?: Buffer().also { threadLocal.set(it) }

        val threadName = Thread.currentThread().name

        logger.v { "BUFFER | REUSE | Thread: $threadName" }

        buffer.clear()
        return buffer
    }
}