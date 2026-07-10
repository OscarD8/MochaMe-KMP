package com.mochame.sync.infrastructure.serialization

import com.mochame.sync.api.VersionRouter
import com.mochame.sync.api.getCodec
import com.mochame.sync.api.latestCodec
import com.mochame.sync.api.models.SyncIntent
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