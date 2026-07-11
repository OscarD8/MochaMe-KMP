package com.mochame.node.fixtures.di

import com.mochame.node.fixtures.FakeNodeContextManager
import com.mochame.sync.spi.node.NodeContextManager
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class FixturesNodeModule {
    @Single(binds = [NodeContextManager::class, FakeNodeContextManager::class])
    fun provideNodeManager(): NodeContextManager = FakeNodeContextManager()
}