package com.mochame.node.fixtures.di

import com.mochame.node.fixtures.FakeBootStatusManager
import com.mochame.node.fixtures.FakeExecutionPolicy
import com.mochame.node.fixtures.FakeIdGenerator
import com.mochame.node.fixtures.FakeNodeContextManager
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.spi.boot.BootStatusUpdater
import com.mochame.sync.spi.node.IdGenerator
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.policy.ExecutionPolicy
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module(includes = [FakeIdGeneratorModule::class])
class FixturesNodeModule {
    @Single(binds = [NodeContextManager::class, FakeNodeContextManager::class])
    fun provideFakeNodeManager(): FakeNodeContextManager = FakeNodeContextManager()

    @Single(binds = [BootStatusProvider::class, BootStatusUpdater::class, FakeBootStatusManager::class])
    fun provideFakeBootStatusManager(): FakeBootStatusManager = FakeBootStatusManager()

    @Single(binds = [ExecutionPolicy::class, FakeExecutionPolicy::class])
    fun provideFakeExecutionPolicy(): FakeExecutionPolicy = FakeExecutionPolicy()
}

@Module
class FakeIdGeneratorModule {
    @Single(binds = [IdGenerator::class, FakeIdGenerator::class])
    fun provideIdGenerator(): IdGenerator = FakeIdGenerator()
}