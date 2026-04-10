package com.mochame.app.data.local.room

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.LinuxTestModules
import com.mochame.app.di.TestTag

@OptIn(ExperimentalKermitApi::class)
class LinuxSyncPersistenceTest: BaseSyncPersistenceTest() {
    override val platformTestModules = listOf(
        LinuxTestModules.databaseModule,
        CoreTestModules.testLoggingModule(platformTag = TestTag.LINUX_X64)
    )
}