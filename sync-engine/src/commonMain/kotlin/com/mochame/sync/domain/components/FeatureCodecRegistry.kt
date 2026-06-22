package com.mochame.sync.domain.components

import com.mochame.sync.contract.LocalFirstEntity
import com.mochame.sync.domain.model.DecodeContext

/**
 * Contract for transforming domain changes into binary bitstreams.
 */
interface FeatureCodecRegistry<T : LocalFirstEntity<T>> {

    /**
     * Delta Generation: Compares the absolute latest state against
     * the new intent. If [old] is null, it encodes a full record.
     */
    fun encode(new: T, old: T?): ByteArray?

    fun decode(data: ByteArray, context: DecodeContext): T

    fun decode(
        data: ByteArray?,
        blobId: String?,
        context: DecodeContext
    ): T  // overflow-aware

    /**
     * Mutation-Time Summary: Used during dispatch for the unencrypted ledger.
     */
    fun summarize(new: T, old: T?): String

    /**
     * Forensic-Time Summary.
     * Generates a manifest from raw bits when domain objects are unavailable.
     */
    fun reconstructSummary(data: ByteArray): String

}