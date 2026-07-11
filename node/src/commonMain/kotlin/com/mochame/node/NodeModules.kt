package com.mochame.node

import com.mochame.contract.di.NodeManagerMutex
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [NodeConcurrencyModule::class])
@ComponentScan("com.mochame.node", "com.mochame.node.data")
class NodeProductionModule

@Module
class NodeConcurrencyModule {
    @Single
    @NodeManagerMutex
    fun provideNodeManagerMutex(): Mutex = Mutex()
}