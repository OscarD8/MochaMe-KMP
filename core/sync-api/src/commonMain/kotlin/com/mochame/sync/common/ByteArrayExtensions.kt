package com.mochame.sync.common

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readLongAt(offset: Int): Long {
    return ((this[offset].toLong() and 0xFFL) shl 56) or
            ((this[offset + 1].toLong() and 0xFFL) shl 48) or
            ((this[offset + 2].toLong() and 0xFFL) shl 40) or
            ((this[offset + 3].toLong() and 0xFFL) shl 32) or
            ((this[offset + 4].toLong() and 0xFFL) shl 24) or
            ((this[offset + 5].toLong() and 0xFFL) shl 16) or
            ((this[offset + 6].toLong() and 0xFFL) shl 8) or
            (this[offset + 7].toLong() and 0xFFL)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readShortAt(offset: Int): Short {
    return (((this[offset].toInt() and 0xFF) shl 8) or
            (this[offset + 1].toInt() and 0xFF)).toShort()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.readIntAt(offset: Int): Int {
    return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.writeLongAt(offset: Int, value: Long) {
    this[offset]     = (value ushr 56).toByte()
    this[offset + 1] = (value ushr 48).toByte()
    this[offset + 2] = (value ushr 40).toByte()
    this[offset + 3] = (value ushr 32).toByte()
    this[offset + 4] = (value ushr 24).toByte()
    this[offset + 5] = (value ushr 16).toByte()
    this[offset + 6] = (value ushr 8).toByte()
    this[offset + 7] = value.toByte()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.writeShortAt(offset: Int, value: Short) {
    val v = value.toInt()
    this[offset]     = (v ushr 8).toByte()
    this[offset + 1] = v.toByte()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.writeIntAt(offset: Int, value: Int) {
    this[offset]     = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}