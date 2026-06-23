package com.mochame.sync.infrastructure.serialization.deprecated

import co.touchlab.kermit.Logger
import com.mochame.sync.contract.models.SyncIntent

abstract class VersionedIntentCodec(
    version: Byte,
    logger: Logger
) : VersionedCodec(version,logger) {

    internal fun encode(intent: SyncIntent): ByteArray {
        return wrapHeader(encodePayload(intent))
    }

    internal fun decode(bytes: ByteArray): SyncIntent {
        return decodePayload(unwrapHeader(bytes))
    }

    protected abstract fun encodePayload(intent: SyncIntent): ByteArray
    protected abstract fun decodePayload(bits: ByteArray): SyncIntent
}