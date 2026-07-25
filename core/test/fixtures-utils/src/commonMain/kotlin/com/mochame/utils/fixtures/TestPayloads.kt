package com.mochame.utils.fixtures

import kotlinx.io.Buffer
import kotlinx.io.Source

object TestPayloads {
    val DEFAULT_TEST_BYTES = byteArrayOf(0x53, 0x54, 0x52, 0x41, 0x4E, 0x44, 0x45, 0x44) // "STRANDED"
    val SMALL_TEXT_BYTES = "Mocha KMP Test Payload Data".encodeToByteArray()
    val LARGE_BINARY_BYTES = ByteArray(16 * 1024) { (it % 256).toByte() }

    fun defaultSource(): Source = Buffer().apply { write(DEFAULT_TEST_BYTES) }
    fun smallTextSource(): Source = Buffer().apply { write(SMALL_TEXT_BYTES) }
    fun largeBinarySource(): Source = Buffer().apply { write(LARGE_BINARY_BYTES) }
}