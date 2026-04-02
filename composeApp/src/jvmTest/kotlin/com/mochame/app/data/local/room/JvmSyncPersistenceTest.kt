package com.mochame.app.data.local.room

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.JVMTestModules

@OptIn(ExperimentalKermitApi::class)
class JvmSyncPersistenceTest: BaseSyncPersistenceTest() {
    override val platformTestModules = listOf(
        JVMTestModules.databaseModule
    )
}