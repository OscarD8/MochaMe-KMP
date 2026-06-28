package com.mochame.sync.contract.models

import com.mochame.contract.exceptions.MochaException
import kotlinx.serialization.Serializable

/**
 * A Hybrid Logical Clock (HLC) timestamp that provides strict ordering across distributed nodes.
 *
 * Serialized format: "ts:count:nodeId"
 *
 * @property ts Wall-clock time in milliseconds.
 * @property count Logical counter used to distinguish events occurring in the same millisecond.
 * @property nodeId Unique identifier for the originating device to prevent collisions.
 */
@Serializable
data class HLC(
    val ts: Long,      // Physical wall-clock time
    val count: Int,     // Logical counter for same-ms events
    val nodeId: String,  // Unique device ID (prevents collisions)
) : Comparable<HLC> {

    /**
     * Converts the HLC to its sortable string representation.
     * TS: 15 digits
     * Count: 5 digits (maps to MAX_COUNTER 65535)
     */
    override fun toString(): String {
        val paddedTs = ts.toString().padStart(15, '0')
        val paddedCount = count.toString(16).uppercase().padStart(4, '0')
        return "$paddedTs:$paddedCount:$nodeId"
    }

    override fun compareTo(other: HLC): Int {
        return compareBy<HLC> { it.ts }
            .thenBy { it.count }
            .thenBy { it.nodeId }
            .compare(this, other)
    }

    companion object {
        /**
         * Safely parses a serialized HLC string.
         * @throws MochaException.Persistent.HlcParseException if not valid.
         */
        fun parse(hlcString: String): HLC {
            val parts = hlcString.split(":")

            if (parts.size != 3) throw MochaException.Persistent.HlcParseException("Format mismatch: $hlcString")

            val ts = parts[0].toLongOrNull()
                ?: throw MochaException.Persistent.HlcParseException("Invalid timestamp: ${parts[0]}")

            val count = parts[1].takeIf { it.length == 4 }?.toIntOrNull(radix = 16)
                ?: throw MochaException.Persistent.HlcParseException("Invalid counter: ${parts[1]}")

            val nodeId = parts[2].takeIf { it.isNotBlank() }
                ?: throw MochaException.Persistent.HlcParseException("NodeId is missing")

            return HLC(ts, count, nodeId)
        }

        /**
         * Necessary as a DailyContext state comes down from the UI layer.
         */
        val EMPTY = HLC(0, 0, "init")

        /**
         * Internal limits
         */
        const val TS_PAD = 15
        const val COUNT_PAD = 5
        const val MAX_COUNTER_INT = 65535
        const val MAX_COUNTER_STRING = "FFFF"
        const val ONE_DAY_MS = 86_400_000L
        const val APP_RELEASE_MS = 1740787200000L
        const val MAX_DRIFT_MS = 60_000L
    }
}
