package com.mochame.sync.infrastructure.serialization.registry

import co.touchlab.kermit.Logger
import com.mochame.sync.contract.models.LocalFirstEntity
import com.mochame.sync.contract.serialization.FeatureCodec
import com.mochame.sync.contract.serialization.FeatureCodecRegistry
import com.mochame.sync.contract.serialization.FeatureRegistryFactory
import com.mochame.sync.contract.serialization.FeatureRoutingRegistry
import org.koin.core.annotation.Single

@Single(binds = [FeatureRegistryFactory::class])
class DefaultFeatureRegistryFactory : FeatureRegistryFactory {
    override fun <T : LocalFirstEntity<T>> create(
        codecMap: Map<Byte, FeatureCodec<T>>,
        latestVersion: Byte,
        logger: Logger
    ): FeatureCodecRegistry<T> {
        return FeatureRoutingRegistry(codecMap, latestVersion, logger)
    }
}