package com.mochame.sync.spi.infrastructure.serialization

import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.common.readIntAt
import com.mochame.sync.common.readLongAt
import com.mochame.sync.common.readShortAt
import com.mochame.sync.common.writeIntAt
import com.mochame.sync.common.writeLongAt
import com.mochame.sync.common.writeShortAt
import kotlin.jvm.JvmInline

@JvmInline
value class FieldHlcIndex(val bytes: ByteArray) {

    fun getHlc(tagId: Int): HLC? {
        val index = findTagIndex(tagId) ?: return null
        val ts = bytes.readLongAt(index + 1)
        val count = bytes.readShortAt(index + 9).toInt()
        val nodeHash = bytes.readIntAt(index + 11)
        return HLC(ts = ts, count = count, nodeId = nodeHash.toString())
    }

    fun updateTag(tagId: Int, hlc: HLC): FieldHlcIndex {
        val index = findTagIndex(tagId)
        val target = index?.let { bytes } ?: bytes.copyOf(bytes.size + RECORD_SIZE)
        val writeIdx = index ?: bytes.size

        target[writeIdx] = tagId.toByte()
        target.writeLongAt(writeIdx + 1, hlc.ts)
        target.writeShortAt(writeIdx + 9, hlc.count.toShort())
        target.writeIntAt(writeIdx + 11, hlc.nodeId.hashCode())

        return FieldHlcIndex(target)
    }

    private fun findTagIndex(tagId: Int): Int? {
        var i = 0
        while (i < bytes.size) {
            if (bytes[i].toInt() == tagId) return i
            i += RECORD_SIZE
        }
        return null
    }

    companion object {
        const val RECORD_SIZE = 15
        val EMPTY = FieldHlcIndex(ByteArray(0))
    }
}