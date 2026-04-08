package com.mochame.app.infrastructure.utils

import kotlinx.io.Buffer

actual class BufferProvider {
    private val threadLocal = ThreadLocal.withInitial { Buffer() }
    actual fun get(): Buffer = threadLocal.get()
}