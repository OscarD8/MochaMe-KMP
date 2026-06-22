package com.mochame.sync.contract

interface CodecRegistryContract<TCodec : Any> {
    val codecMap: Map<Byte, TCodec>
    val latestVersion: Byte

    val latestCodec: TCodec
        get() = codecMap[latestVersion]
            ?: throw IllegalStateException("No codec registered for version $latestVersion")
}