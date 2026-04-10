package com.mochame.app.orchestration

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.AndroidHostTestModules
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.TestTag
import com.mochame.app.orchestration.sync.BaseSyncJanitorTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKermitApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidHostSyncJanitorTests: BaseSyncJanitorTest() {
    override val platformTestModules = listOf(
        AndroidHostTestModules.databaseModule,
        CoreTestModules.testLoggingModule(TestTag.ANDROID_HOST)
    )
}