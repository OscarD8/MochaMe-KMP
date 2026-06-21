package com.mochame.sync.infrastructure.serialization.intent

import co.touchlab.kermit.Logger
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.infrastructure.serialization.BaseVersionedCodec

abstract class BaseIntentCodec(
    version: Byte,
    logger: Logger
) : BaseVersionedCodec(version,logger) {

    internal fun encode(intent: SyncIntent): ByteArray {
        return wrapHeader(encodePayload(intent))
    }

    internal fun decode(bytes: ByteArray): SyncIntent {
        return decodePayload(unwrapHeader(bytes))
    }

    protected abstract fun encodePayload(intent: SyncIntent): ByteArray
    protected abstract fun decodePayload(bits: ByteArray): SyncIntent
}