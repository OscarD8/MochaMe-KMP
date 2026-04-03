package com.mochame.app.domain.system.sync

/**
 * Contract for transforming domain changes into binary bitstreams.
 */
interface PayloadEncoder<T> {
    /**
     * Generates a binary delta (Partial Diff) between two states.
     * Implementation should use kotlinx-serialization-protobuf.
     */
    fun encodeDiff(old: T?, new: T): ByteArray

    /**
     * Generates the binary representation for a deletion intent.
     */
    fun encodeDelete(): ByteArray
}