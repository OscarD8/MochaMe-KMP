package com.mochame.sync.domain.providers

import kotlinx.io.Buffer

/**
 * A platform-agnostic way to get a thread-safe reusable buffer.
 * Gemini argued that this is helpful to prevent memory churn
 * from potentially many concurrent calls to reconstruct/encode
 * sync payloads. Buffers are not thread safe, so the intent
 * here is to establish a thread-safe single buffer object
 * that any given thread will reuse during its lifecycle.
 * The process changes from creating and garbage collecting
 * a different buffer per reconstruction/encode, to each thread
 * reusing its own buffer, clearing and re-writing to it.
  */
interface BufferProvider {
    fun get(): Buffer
}