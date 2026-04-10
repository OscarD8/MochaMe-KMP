package com.mochame.app.orchestration

import androidx.test.ext.junit.runners.AndroidJUnit4
import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.AndroidDeviceTestModules
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.TestTag
import com.mochame.app.orchestration.sync.BaseSyncJanitorTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.koin.core.module.Module

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKermitApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidDeviceSyncJanitorTests: BaseSyncJanitorTest() {
    override val platformTestModules: List<Module> = listOf(
        AndroidDeviceTestModules.databaseModule,
        CoreTestModules.testLoggingModule(TestTag.ANDROID_DEVICE)
    )
}