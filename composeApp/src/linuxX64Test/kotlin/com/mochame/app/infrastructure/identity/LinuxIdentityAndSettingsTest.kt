package com.mochame.app.infrastructure.identity

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.LinuxTestModules
import com.mochame.app.di.TestTag
import org.koin.core.module.Module

@OptIn(ExperimentalKermitApi::class)
class LinuxIdentityAndSettingsTest : BaseIdentityAndSettingsTest() {
    override val platformModules: List<Module> = listOf(
        LinuxTestModules.databaseModule,
        CoreTestModules.testLoggingModule(platformTag = TestTag.LINUX_X64)
    )
}
