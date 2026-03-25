package com.mochame.app.infrastructure.sync

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.AndroidHostTestModules
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.CoreTestModules.testLoggingModule
import com.mochame.app.di.TestTag
import com.mochame.app.infrastructure.sync.BaseSyncJanitorTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKermitApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidHostSyncJanitorTests: BaseSyncJanitorTest() {
    override val platformTestModules = listOf(
        AndroidHostTestModules.databaseModule,
        testLoggingModule(TestTag.ANDROIDHOST)
    )
}