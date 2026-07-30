package com.mochame.sync.infrastructure.serialization

import com.mochame.sync.domain.serialization.IntentCodec
import com.mochame.sync.domain.serialization.IntentCodecRouter
import com.mochame.sync.spi.infrastructure.serialization.getCodec
import com.mochame.sync.spi.infrastructure.serialization.latestCodec
import com.mochame.sync.spi.models.SyncIntent
import org.koin.core.annotation.Single

@Single(binds = [IntentCodecRouter::class])
internal class DefaultIntentCodecRouter(
    v1: IntentCodecV1,
) : IntentCodecRouter {

    override val versionRegistry = arrayOf<IntentCodec?>(null, v1)
    override val latestVersion = 1

    override fun routedEncode(intent: SyncIntent): ByteArray = latestCodec.encode(intent)

    override fun routedDecode(bytes: ByteArray, version: Int): SyncIntent =
        getCodec(version).decode(bytes)

}