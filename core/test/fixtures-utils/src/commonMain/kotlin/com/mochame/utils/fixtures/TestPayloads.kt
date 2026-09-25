package com.mochame.utils.fixtures

import kotlinx.io.Buffer
import kotlinx.io.Source

object TestPayloads {
    val DEFAULT = "Mocha".encodeToByteArray()
    val SMALL_TEXT_BYTES = "Mocha KMP Test Payload Data".encodeToByteArray()
    val LARGE_BINARY_BYTES = ByteArray(16 * 1024) { (it % 256).toByte() }

    fun defaultSource(): Source = Buffer().apply { write(DEFAULT) }
    fun smallTextSource(): Source = Buffer().apply { write(SMALL_TEXT_BYTES) }
    fun largeBinarySource(): Source = Buffer().apply { write(LARGE_BINARY_BYTES) }

    fun sourceFromString(content: String): Source = Buffer().apply {
        write(content.encodeToByteArray())
    }
}