package com.mochame.sync.contract.serialization

import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.LocalFirstEntity
import kotlinx.io.Source

interface FeatureCodec<T : LocalFirstEntity<T>> {
    fun encode(new: T, old: T?): ByteArray?
    fun decode(bytes: ByteArray, context: DecodeContext): T
    
    /**
     * MUST use a peeked source view (source.peek()) to guarantee 
     * that forensic ledger parsing never advances the master stream cursor.
     */
    fun reconstructSummary(bytes: ByteArray): String
    fun summarize(new: T, old: T?): String
}
