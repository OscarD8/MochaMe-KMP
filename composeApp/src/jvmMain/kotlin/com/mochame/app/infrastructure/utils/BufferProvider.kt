package com.mochame.app.infrastructure.utils

import kotlinx.io.Buffer

class JvmBufferProvider : BufferProvider {
    private val threadLocal = ThreadLocal.withInitial { Buffer() }
    override fun get(): Buffer = threadLocal.get()
}