package com.mochame.app.orchestration

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.LinuxTestModules
import com.mochame.app.di.TestTag
import com.mochame.app.orchestration.sync.BaseSyncJanitorTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.core.module.Module

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKermitApi::class)
class LinuxSyncJanitorTest: BaseSyncJanitorTest() {
    override val platformTestModules: List<Module> = listOf(
        LinuxTestModules.databaseModule,
        CoreTestModules.testLoggingModule(platformTag = TestTag.LINUX_X64)
    )
}
