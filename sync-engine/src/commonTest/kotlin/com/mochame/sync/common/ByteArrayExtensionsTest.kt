package com.mochame.sync.common

import com.mochame.support.MochaPlatformTest
import kotlin.test.Test
import kotlin.test.assertEquals


class ByteArrayExtensionsTest : MochaPlatformTest() {

    // -------------------------------------------------------------------
    // WRITES / LONG
    // -------------------------------------------------------------------
    @Test
    fun should_writeBytesInExplicitBigEndianOrder_when_writeLongAtCalledAtOffsetZero() {
        // Given
        val bytes = ByteArray(8)
        val value = 0x0102030405060708L

        // When
        bytes.writeLongAt(offset = 0, value = value)

        // Then
        bytes.assertBytesAt(
            offset = 0,
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
        )
    }

    @Test
    fun should_writeBytesInExplicitBigEndianOrder_when_writeLongAtCalledAtNonZeroOffset() {
        // Given
        val bytes = ByteArray(12)
        val value = 0x0102030405060708L

        // When
        bytes.writeLongAt(offset = 4, value = value)

        // Then
        bytes.assertBytesAt(
            offset = 4,
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
        )
    }

    @Test
    fun should_roundTripLongValuesWithoutLoss_when_writingAndReadingExtremes() {
        val extremeValues = listOf(
            0L,
            -1L,
            Long.MAX_VALUE, // 0x7FFFFFFFFFFFFFFF
            Long.MIN_VALUE, // 0x8000000000000000
            0x0123456789ABCDEFL
        )

        val bytes = ByteArray(8)

        for (value in extremeValues) {
            // When
            bytes.writeLongAt(offset = 0, value = value)
            val actual = bytes.readLongAt(offset = 0)

            // Then
            assertEquals(
                expected = value,
                actual = actual,
                message = "Failed to round-trip 64-bit Long value: $value (0x${value.toString(16)})"
            )
        }
    }


}

// -------------------------------------------------------------------
// HELPERS
// -------------------------------------------------------------------

/**
 * Asserts that [expected] bytes match the slice in [ByteArray] starting at [offset].
 * Accepts [Int] hex literals (e.g., 0x80, 0xFF).
 */
internal fun ByteArray.assertBytesAt(offset: Int, vararg expected: Int) {
    expected.forEachIndexed { i, expectedInt ->
        val actualIndex = offset + i
        val expectedByte = expectedInt.toByte()
        val actualByte = this[actualIndex]

        assertEquals(
            expected = expectedByte,
            actual = actualByte,
            message = "Mismatch at offset $actualIndex (relative index $i). Expected 0x${expectedInt.toHex()}, got 0x${actualByte.toHex()}"
        )
    }
}

internal fun Int.toHex(): String =
    (this and 0xFF).toString(16).padStart(2, '0').uppercase()
internal fun Byte.toHex(): String =
    (this.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()