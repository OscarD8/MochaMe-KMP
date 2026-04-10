package com.mochame.app.infrastructure.utils

import co.touchlab.kermit.Logger
import kotlinx.io.Buffer
import kotlin.native.concurrent.ThreadLocal

class ThreadPinnedBufferProvider(private val logger: Logger) : BufferProvider {
    @ThreadLocal
    companion object {
        // Every Linux thread gets its own private, persistent Buffer instance
        private val threadInstance = Buffer()
    }

    override fun get(): Buffer {
        threadInstance.clear()
        logger.v { "BUFFER | REUSE | Thread: ${platform.posix.pthread_self()}" }
        return threadInstance
    }

}