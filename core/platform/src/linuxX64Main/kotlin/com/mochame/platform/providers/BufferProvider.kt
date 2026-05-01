package com.mochame.platform.providers

import co.touchlab.kermit.Logger
import kotlinx.io.Buffer
import org.koin.core.annotation.Single
import platform.posix.pthread_self
import kotlin.native.concurrent.ThreadLocal

@Single
class LinuxBufferProvider(private val logger: Logger) : BufferProvider {
    @ThreadLocal
    companion object {
        private val threadInstance = Buffer()
    }

    override fun get(): Buffer {
        threadInstance.clear()
        logger.v { "BUFFER | REUSE | Thread: ${pthread_self()}" }
        return threadInstance
    }

}