package com.mochame.node.di

import co.touchlab.kermit.Logger
import com.mochame.annotations.NodeManagerMutex
import com.mochame.logger.LoggerModule
import com.mochame.node.policies.StaggeredDbRetryPolicy
import com.mochame.platform.di.CommonPlatformModule
import com.mochame.sync.spi.policy.ExecutionPolicy
import com.mochame.utils.di.UtilsModule
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        UtilsModule::class,
        LoggerModule::class,
        StaggeredDbRetryPolicyModule::class,
        CommonPlatformModule::class
    ]
)
@ComponentScan("com.mochame.node")
class NodeProductionModule {

    @Single
    @NodeManagerMutex
    fun provideNodeManagerMutex(): Mutex = Mutex()
}

@Module(includes = [LoggerModule::class])
class StaggeredDbRetryPolicyModule {
    @Single(binds = [ExecutionPolicy::class])
    fun provideStaggeredDbPolicyModule(logger: Logger): ExecutionPolicy =
        StaggeredDbRetryPolicy(logger = logger)
}