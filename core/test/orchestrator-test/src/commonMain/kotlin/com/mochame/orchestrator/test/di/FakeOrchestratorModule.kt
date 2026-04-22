package com.mochame.orchestrator.test.di

import com.mochame.orchestrator.BootStatusProvider
import com.mochame.orchestrator.BootStatusUpdater
import com.mochame.orchestrator.GlobalMetadataStore
import com.mochame.orchestrator.test.FakeGlobalMetaStore
import com.mochame.orchestrator.test.FakeIdGenerator
import com.mochame.orchestrator.BootStatusManager
import com.mochame.orchestrator.IdentityManager
import com.mochame.utils.IdGenerator
import org.koin.core.annotation.Module
import org.koin.core.module.dsl.binds
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Provides trackable ID Generation and a metadata store with no database interaction.
 */
@Module
class OrchestratorTestModule {
    val definitions = module {
        single<GlobalMetadataStore> { FakeGlobalMetaStore() }
        single<IdGenerator> { FakeIdGenerator() }

        singleOf(::IdentityManager)

        singleOf(::BootStatusManager) {
            binds(
                listOf(BootStatusProvider::class, BootStatusUpdater::class)
            )
        }
    }
}