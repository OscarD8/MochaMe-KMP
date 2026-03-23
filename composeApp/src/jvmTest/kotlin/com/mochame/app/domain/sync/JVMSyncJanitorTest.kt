package com.mochame.app.domain.sync

import com.mochame.app.di.JVMTestModules
import org.koin.core.module.Module

class JVMSyncJanitorTest: BaseSyncJanitorTest() {
    override val platformTestModules: List<Module> = listOf(
        JVMTestModules.databaseModule,
        JVMTestModules.loggerModule
    )
}