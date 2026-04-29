package com.mochame.contract.fixtures.di

import com.mochame.contract.boot.BootStatusProvider
import com.mochame.contract.boot.BootStatusUpdater
import com.mochame.contract.fixtures.FakeBootStatusManager
import com.mochame.contract.identity.GlobalMetadataStore
import com.mochame.contract.fixtures.FakeGlobalMetaStore
import com.mochame.contract.fixtures.FakeIdGenerator
import com.mochame.contract.fixtures.FakeIdentityManager
import com.mochame.contract.identity.IdGenerator
import com.mochame.contract.identity.IdentityManager
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module(
    includes = [
        FixtureIdentityProviderModule::class,
        FixtureFakeBootManager::class,
        FixtureFakeIdentityManager::class
    ]
)
class FixturesContractModule

/**
 * Provides the fakes necessary to test IdentityManager logic.
 */
@Module
class FixtureIdentityProviderModule {
    @Single
    fun provideMetaStore(): GlobalMetadataStore = FakeGlobalMetaStore()
    @Single
    fun provideIdGenerator(): IdGenerator = FakeIdGenerator()
}

@Module
class FixtureFakeBootManager {
    @Single(binds = [BootStatusProvider::class, BootStatusUpdater::class])
    fun provideFakeBootStatusManager(): FakeBootStatusManager = FakeBootStatusManager()
}

/**
 * Provides a fake identity manager.
 */
@Module
class FixtureFakeIdentityManager {
    @Single(binds = [IdentityManager::class])
    fun provideFakeIdentityManager(): FakeIdentityManager = FakeIdentityManager()
}