package com.mochame.sync.contract.serialization

import co.touchlab.kermit.Logger
import com.mochame.sync.contract.models.LocalFirstEntity

interface FeatureRegistryFactory {
    fun <T : LocalFirstEntity<T>> create(
        codecMap: Map<Byte, FeatureCodec<T>>,
        latestVersion: Byte,
        logger: Logger
    ): FeatureCodecRegistry<T>
}