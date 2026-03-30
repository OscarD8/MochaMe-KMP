package com.mochame.app.data.local.room.dao

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.JVMTestModules
import com.mochame.app.di.TestTag
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKermitApi::class)
class JvmBioDaoTest : BaseBioDaoTest() {
    override val platformTestModules = listOf(
        JVMTestModules.databaseModule,
        CoreTestModules.testLoggingModule(TestTag.JVM)
    )
}