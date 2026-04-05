package com.mochame.app.domain.system.sync

import com.mochame.app.domain.system.sync.LocalFirstEntity

/**
 * Contract for transforming domain changes into binary bitstreams.
 */
interface PayloadEncoder<T : LocalFirstEntity<T>> {
    /**
     * Delta Generation: Compares the absolute latest state against
     * the new intent. If [old] is null, it encodes a full record.
     */
    fun encode(new: T, old: T?): ByteArray

    /**
     * Diagnostic Transparency: Generates a non-sensitive string
     * for the unencrypted 'diagnosticSummary' field.
     */
    fun summarize(new: T): String

    /**
     * Terminal Failure Signal: Returns a specific error if
     * version mismatch or corruption is detected.
     */
    fun validate(data: ByteArray): Boolean
}