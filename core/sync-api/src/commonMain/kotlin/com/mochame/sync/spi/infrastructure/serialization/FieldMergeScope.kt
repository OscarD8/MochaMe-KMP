package com.mochame.sync.spi.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.common.hasTag

/**
 * Scope provided to feature codecs during field-level delta merging.
 * Manages LWW evaluation and binary blob construction.
 */
class FieldMergeScope(
    existingBytes: ByteArray,
    val incomingHlc: HLC,
    val changedMask: Long,
    val logger: Logger
) {
    @PublishedApi
    internal var index = FieldHlcMap(existingBytes)

    /**
     * Field LWW Rule:
     * - If field is missing from delta through Protocol Buffer implicit field usage (null), retain existing value.
     * - If field is present, accept if no local tag HLC exists OR incoming HLC > local tag HLC.
     * - Otherwise, reject incoming value and retain existing local value.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun <V> eval(tagId: Int, incomingVal: V?, existingVal: V?): V? {
        if (!changedMask.hasTag(tagId)) {
            return existingVal
        }
        
        val localTagHlc = index.getHlc(tagId)

        return if (localTagHlc == null || incomingHlc > localTagHlc) {
            index = index.updateTag(tagId, incomingHlc)
            incomingVal
        } else {
            logger.v { "Field conflict lost [tag=$tagId]. Local HLC ($localTagHlc) >= Inbound ($incomingHlc)" }
            existingVal
        }
    }

    internal fun getTagHlc(tagId: Int): HLC? = index.getHlc(tagId)

    internal fun updateTag(tagId: Int, hlc: HLC) {
        index = index.updateTag(tagId, hlc)
    }

    internal fun buildResultBlob(): ByteArray = index.bytes
}