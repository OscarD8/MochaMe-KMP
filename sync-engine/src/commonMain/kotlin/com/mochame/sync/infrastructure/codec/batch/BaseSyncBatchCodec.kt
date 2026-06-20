package com.mochame.sync.infrastructure.codec.batch

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.infrastructure.codec.intent.BaseSyncIntentCodec



abstract class BaseSyncBatchCodec(
    protected val version: Byte,
    protected val intentCodec: BaseSyncIntentCodec,
    protected val logger: Logger
) {

    /**
     * Wraps the internal codecs serialization design with the version number.
     */
    internal fun encode(intents: List<SyncIntent>): ByteArray {
        val payloadBytes = encodePayload(intents)
        val transportPacket = ByteArray(1 + payloadBytes.size)
        transportPacket[0] = version
        payloadBytes.copyInto(transportPacket, destinationOffset = 1)

        return transportPacket
    }

    /**
     * Wraps the internal codecs deserialization design, extracting the version.
     */
    internal fun decode(bytes: ByteArray): List<SyncIntent> {
        if (!validate(bytes)) throw MochaException.Persistent.CorruptionDetected(
            "Batch size contained only a version, or failed a further protocol check."
        )

        val payloadBytes = bytes.copyOfRange(1, bytes.size)
        return decodePayload(payloadBytes)
    }

    // --- Subclass Responsibility ---

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
    protected fun validate(bytes: ByteArray) : Boolean =
        bytes.size > 1 && bytes[0] == version
}