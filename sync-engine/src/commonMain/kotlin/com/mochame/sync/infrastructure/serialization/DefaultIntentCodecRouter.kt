package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.VersionRouter
import com.mochame.sync.contract.getCodec
import com.mochame.sync.contract.latestCodec
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.stripAndVersion
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