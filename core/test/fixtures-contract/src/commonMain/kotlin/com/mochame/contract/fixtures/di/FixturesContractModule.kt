package com.mochame.contract.fixtures.di

import com.mochame.contract.boot.BootStatusProvider
import com.mochame.contract.boot.BootStatusUpdater
import com.mochame.contract.fixtures.FakeBootStatusManager
import com.mochame.contract.node.NodeContextStore
import com.mochame.contract.fixtures.FakeNodeContextStore
import com.mochame.contract.fixtures.FakeIdGenerator
import com.mochame.contract.fixtures.FakeNodeContextManager
import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.contract.node.IdGenerator
import com.mochame.contract.node.NodeContextManager
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module(
    includes = [
        FixtureIdentityProviderModule::class,
        FakeBootManagerModule::class,
        FakeIdentityManagerModule::class,
    ]
)
class FixturesContractModule

/**
 * Provides the fakes necessary to test IdentityManager logic.
 */
@Module
class FixtureIdentityProviderModule {
    @Single(binds = [NodeContextStore::class, FakeNodeContextStore::class])
    fun provideMetaStore(): NodeContextStore = FakeNodeContextStore()
    @Single(binds = [IdGenerator::class, FakeIdGenerator::class])
    fun provideIdGenerator(): IdGenerator = FakeIdGenerator()
}

@Module
class FakeBootManagerModule {
    @Single(binds = [BootStatusProvider::class, BootStatusUpdater::class])
    fun provideFakeBootStatusManager(): FakeBootStatusManager = FakeBootStatusManager()
}

/**
 * Provides a fake identity manager.
 */
@Module
class FakeIdentityManagerModule {
    @Single(binds = [NodeContextManager::class, FakeNodeContextManager::class])
    fun provideFakeIdentityManager(): FakeNodeContextManager = FakeNodeContextManager()
}
