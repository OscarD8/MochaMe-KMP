package com.mochame.app.infrastructure.identity

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.JVMTestModules
import org.koin.core.module.Module

@OptIn(ExperimentalKermitApi::class)
class JVMIdentityAndSettingsTest : BaseIdentityAndSettingsTest() {
    override val platformModules: List<Module> = listOf(
        JVMTestModules.databaseModule
    )
}