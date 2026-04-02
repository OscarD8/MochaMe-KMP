package com.mochame.app.data.local.room

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.AndroidHostTestModules
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalKermitApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidHostSyncPersistenceTest: BaseSyncPersistenceTest() {
    override val platformTestModules = listOf(
        AndroidHostTestModules.databaseModule
    )
}