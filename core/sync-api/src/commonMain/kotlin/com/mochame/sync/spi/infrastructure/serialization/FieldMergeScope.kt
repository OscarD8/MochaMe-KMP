package com.mochame.sync.spi.infrastructure.serialization

import com.mochame.sync.api.hlc.HLC

/**
 * Scope provided to feature codecs during field-level delta merging.
 * Manages LWW evaluation and binary blob construction transparently.
 */
class FieldMergeScope(
    existingBytes: ByteArray,
    val incomingHlc: HLC
) {
    private var fieldMap = FieldHlcIndex(existingBytes)

    /**
     * Evaluates an incoming field value against the local field timestamp.
     * Accepts incomingVal if incoming HLC > local tag HLC (or tag is untracked).
     * Retains existingVal if local tag HLC is newer.
     */
    fun <V> eval(tagId: Int, incomingVal: V?, existingVal: V): V {
        if (incomingVal == null) return existingVal
        val localTagHlc = fieldMap.getHlc(tagId)

        return if (localTagHlc == null || incomingHlc > localTagHlc) {
            fieldMap = fieldMap.updateTag(tagId, incomingHlc)
            incomingVal
        } else {
            existingVal
        }
    }

    @PublishedApi
    internal fun buildResultBlob(): ByteArray = fieldMap.bytes
}