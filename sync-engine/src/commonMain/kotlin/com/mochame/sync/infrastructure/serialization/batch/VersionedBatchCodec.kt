package com.mochame.sync.infrastructure.serialization.batch

import co.touchlab.kermit.Logger
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.infrastructure.serialization.VersionedCodec
import com.mochame.sync.infrastructure.serialization.intent.VersionedIntentCodec


abstract class VersionedBatchCodec(
    version: Byte,
    logger: Logger,
    protected val intentCodec: VersionedIntentCodec
) : VersionedCodec(version, logger) {

    /**
     * Wraps the internal codecs serialization design with the version number.
     */
    internal fun encode(intents: List<SyncIntent>): ByteArray =
        wrapHeader(encodePayload(intents))

    /**
     * Wraps the internal codecs deserialization design, extracting the version.
     */
    internal fun decode(bytes: ByteArray): List<SyncIntent> {
        return decodePayload(unwrapHeader(bytes))
    }

    /**
     * Accepts a list of [SyncIntent] models to be iterated over and individually
     * encoded using the implementing [intentCodec], then a specified serializable model
     * used to encode the resulting List of Bytes alongside their size.
     * @return ByteArray - serialized delta model encasing the array of all intents in the
     * batch with a total size.
     */
    protected abstract fun encodePayload(intents: List<SyncIntent>): ByteArray

    /**
     * Accepts a ByteArray, to be decoded utilizing the implementing batch codecs delta model.
     * Once the batch is decoded, it must use [intentCodec] to pass back a list of
     * deserialized [SyncIntent] models.
     */
    protected abstract fun decodePayload(bytes: ByteArray): List<SyncIntent>

}