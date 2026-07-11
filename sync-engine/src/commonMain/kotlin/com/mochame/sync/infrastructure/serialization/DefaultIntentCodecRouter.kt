package com.mochame.sync.infrastructure.serialization

import com.mochame.sync.spi.serialization.VersionRouter
import com.mochame.sync.spi.serialization.getCodec
import com.mochame.sync.spi.serialization.latestCodec
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.domain.serialization.IntentCodec
import com.mochame.sync.domain.serialization.IntentCodecRouter
import org.koin.core.annotation.Single

@Single(binds = [IntentCodecRouter::class])
internal class DefaultIntentCodecRouter(
    v1: IntentCodecV1,
) : VersionRouter<IntentCodec>, IntentCodecRouter {

    override val versionRegistry = arrayOf<IntentCodec?>(null, v1)
    override val latestVersion = 1

    override fun routedEncode(intent: SyncIntent): ByteArray {
        return latestCodec.encode(intent)
    }

    override fun routedDecode(bytes: ByteArray, version: Int): SyncIntent {
        return getCodec(version).decode(bytes)
    }
}