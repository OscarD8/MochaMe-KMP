package com.mochame.app.infrastructure.sync

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.CoreTestModules.testLoggingModule
import com.mochame.app.di.JVMTestModules
import com.mochame.app.di.TestTag
import com.mochame.app.orchestration.sync.BaseSyncJanitorTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.core.module.Module

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKermitApi::class)
class JVMSyncJanitorTest: BaseSyncJanitorTest() {
    override val platformTestModules: List<Module> = listOf(
        JVMTestModules.databaseModule,
        testLoggingModule(platformTag = TestTag.JVM)
    )
}