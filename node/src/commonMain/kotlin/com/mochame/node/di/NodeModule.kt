package com.mochame.node.di

import com.mochame.contract.di.NodeManagerMutex
import com.mochame.logger.LoggerModule
import com.mochame.platform.di.CommonPlatformModule
import com.mochame.utils.di.UtilsModule
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        NodeConcurrencyModule::class,
        UtilsModule::class,
        LoggerModule::class,
        CommonPlatformModule::class
    ]
)
@ComponentScan("com.mochame.node")
class NodeProductionModule

@Module
class NodeConcurrencyModule {
    @Single
    @NodeManagerMutex
    fun provideNodeManagerMutex(): Mutex = Mutex()
}