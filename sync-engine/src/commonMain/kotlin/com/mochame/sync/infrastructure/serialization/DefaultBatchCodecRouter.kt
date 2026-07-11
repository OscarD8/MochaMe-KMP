package com.mochame.sync.infrastructure.serialization

import com.mochame.sync.spi.serialization.getCodec
import com.mochame.sync.spi.serialization.latestCodec
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.domain.serialization.BatchCodec
import com.mochame.sync.domain.serialization.BatchCodecRouter
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.annotation.Single

@ExperimentalSerializationApi
@Single(binds = [BatchCodecRouter::class])
internal class DefaultBatchCodecRouter(
    v1: BatchCodecV1
) : BatchCodecRouter {

    override val versionRegistry = arrayOf<BatchCodec?>(null, v1)

    /*
     question: The Java Virtual Machine maintains a permanent, pre-allocated internal array of java.lang.Byte objects on the heap for every single value from -128 to 127.
     */
    /**
     * Byte remains a primitive sitting inside this object's memory block on the heap as raw bytes, not a boxed object
     * pointer as was previously the case when using a map of <Byte, Codec>.
     * This means the CPU copies the 8 bits straight into their registers to perform operations.
     * Using a Byte means a max value of 255 (due to bit masking on the versionRegistry lookup), an Int would
     * mean a 4-byte version system on each payload component, which is not necessary.
     */
    override val latestVersion = 1

    override fun routedEncode(intents: List<SyncIntent>): ByteArray {
        return latestCodec.encode(intents)
    }

    override fun routedDecode(bytes: ByteArray, version: Int): List<SyncIntent> {
        return getCodec(version).decode(bytes)
    }
}