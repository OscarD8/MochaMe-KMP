package com.mochame.node.fixtures.di

import com.mochame.node.fixtures.FakeBootStatusManager
import com.mochame.node.fixtures.FakeNodeContextManager
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.spi.boot.BootStatusUpdater
import com.mochame.sync.spi.node.NodeContextManager
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
class FixturesNodeModule {
    @Single(binds = [NodeContextManager::class, FakeNodeContextManager::class])
    fun provideFakeNodeManager(): NodeContextManager = FakeNodeContextManager()

    @Single(binds = [BootStatusUpdater::class, FakeBootStatusManager::class])
    fun provideFakeBootStatusProvider(): BootStatusProvider = FakeBootStatusManager()

    @Single(binds = [BootStatusProvider::class, FakeBootStatusManager::class])
    fun provideFakeBootStatusManager(): BootStatusUpdater = FakeBootStatusManager()
}
