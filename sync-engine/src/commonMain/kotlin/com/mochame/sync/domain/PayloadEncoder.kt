package com.mochame.sync.domain

import com.mochame.sync.domain.model.EntityMetadata
import com.mochame.sync.domain.model.LocalFirstEntity


/**
 * Contract for transforming domain changes into binary bitstreams.
 */
interface PayloadEncoder<T : LocalFirstEntity<T>> {
    /**
     * Delta Generation: Compares the absolute latest state against
     * the new intent. If [old] is null, it encodes a full record.
     */
    fun encode(new: T, old: T?): ByteArray?

    /**
     * Mutation-Time Summary: Used during dispatch for the unencrypted ledger.
     */
    fun summarize(new: T, old: T?): String

    /**
     * Forensic-Time Summary.
     * Generates a manifest from raw bits when domain objects are unavailable.
     */
    fun reconstructSummary(data: ByteArray): String

    /**
     * Terminal Failure Signal: Returns a specific error if
     * version mismatch or corruption is detected.
     */
    fun validate(data: ByteArray): Boolean

    fun decode(data: ByteArray, metadata: EntityMetadata): T

}