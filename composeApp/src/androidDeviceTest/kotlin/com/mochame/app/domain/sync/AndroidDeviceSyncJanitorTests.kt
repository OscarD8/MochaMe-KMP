package com.mochame.app.domain.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochame.app.di.AndroidDeviceTestModules
import org.junit.runner.RunWith
import org.koin.core.module.Module

@RunWith(AndroidJUnit4::class)
class AndroidDeviceSyncJanitorTests: BaseSyncJanitorTest() {
    override val platformTestModules: List<Module> = listOf(
        AndroidDeviceTestModules.databaseModule,
        AndroidDeviceTestModules.loggerModule
    )
}