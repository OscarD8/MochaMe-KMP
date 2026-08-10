package com.mochame.sync.common

// -------------------------------------------------------------------
// Expects that the instance (which is endian-neutral) has been
// written in Big-Endian.
// -------------------------------------------------------------------

/**
 * Reading a Big-Endian signed Long.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readLongAt(offset: Int): Long =
    ((this[offset].toLong() and 0xFFL) shl 56) or
            ((this[offset + 1].toLong() and 0xFFL) shl 48) or
            ((this[offset + 2].toLong() and 0xFFL) shl 40) or
            ((this[offset + 3].toLong() and 0xFFL) shl 32) or
            ((this[offset + 4].toLong() and 0xFFL) shl 24) or
            ((this[offset + 5].toLong() and 0xFFL) shl 16) or
            ((this[offset + 6].toLong() and 0xFFL) shl 8) or
            (this[offset + 7].toLong() and 0xFFL)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readULong(offset: Int): ULong =
    (((this[offset].toLong() and 0xFFL) shl 56) or
            ((this[offset + 1].toLong() and 0xFFL) shl 48) or
            ((this[offset + 2].toLong() and 0xFFL) shl 40) or
            ((this[offset + 3].toLong() and 0xFFL) shl 32) or
            ((this[offset + 4].toLong() and 0xFFL) shl 24) or
            ((this[offset + 5].toLong() and 0xFFL) shl 16) or
            ((this[offset + 6].toLong() and 0xFFL) shl 8) or
            (this[offset + 7].toLong() and 0xFFL)).toULong()

/**
 * Reading a Big-Endian unsigned short.
 * Because 16 bits are packed into a 32-bit Int, the highest bit of the 16-bit number sits at bit position 15.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readUShortAt(offset: Int): UShort =
    (((this[offset].toInt() and 0xFF) shl 8) or
            (this[offset + 1].toInt() and 0xFF)).toUShort()

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readShortAt(offset: Int): Short =
    (((this[offset].toInt() and 0xFF) shl 8) or
            (this[offset + 1].toInt() and 0xFF)).toShort()
@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readIntAt(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readUIntAt(offset: Int): UInt =
    (((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)).toUInt()


/**
 * [ushr] to process bytes from highest value first (assuming Big Endian),
 * slicing them with [toByte], and placing that isolated Byte at the offset.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.writeLongAt(offset: Int, value: Long) {
    this[offset] = (value ushr 56).toByte()
    this[offset + 1] = (value ushr 48).toByte()
    this[offset + 2] = (value ushr 40).toByte()
    this[offset + 3] = (value ushr 32).toByte()
    this[offset + 4] = (value ushr 24).toByte()
    this[offset + 5] = (value ushr 16).toByte()
    this[offset + 6] = (value ushr 8).toByte()
    this[offset + 7] = value.toByte()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.writeShortAt(offset: Int, value: Int) {
    this[offset] = (value ushr 8).toByte()
    this[offset + 1] = value.toByte()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.writeIntAt(offset: Int, value: Int) {
    this[offset] = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}