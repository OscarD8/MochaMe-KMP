package com.mochame.app.domain.sync

import com.mochame.app.di.AndroidHostTestModules
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidHostSyncJanitorTests: BaseSyncJanitorTest() {
    override val platformTestModules = listOf(
        AndroidHostTestModules.databaseModule,
        AndroidHostTestModules.loggerModule
    )
}