package com.mochame.app.infrastructure.utils

import kotlinx.io.Buffer

class AndroidBufferProvider: BufferProvider {
    private val threadLocal = ThreadLocal.withInitial { Buffer() }
    override fun get(): Buffer = threadLocal.get() ?: Buffer().also { threadLocal.set(it) }
}