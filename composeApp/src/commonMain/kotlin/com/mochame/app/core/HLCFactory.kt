package com.mochame.app.core

// commonMain
data class HLC(
    val ts: Long,      // Physical wall-clock time
    val count: Int,     // Logical counter for same-ms events
    val nodeId: String,  // Unique device ID (prevents collisions)
) {
    override fun toString(): String = "$ts:$count:$nodeId"

    companion object {
        fun parse(hlcString: String): HLC {
            val parts = hlcString.split(":")
            return HLC(parts[0].toLong(), parts[1].toInt(), parts[2])
        }
    }
}

class HlcFactory(
    private val nodeId: String,
    private val dateTimeUtils: DateTimeUtils = DateTimeUtils()
) {
    private var lastHlc = HLC(0, 0, nodeId)

    fun now(): String {
        val systemTime = dateTimeUtils.now().toEpochMilliseconds()

        lastHlc = synchronized(this) {
            val newTs = maxOf(systemTime, lastHlc.ts)
            val newCount = if (newTs == lastHlc.ts) lastHlc.count + 1 else 0
            HLC(newTs, newCount, nodeId)
        }
        return lastHlc.toString()
    }
}