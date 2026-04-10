package com.mochame.app.data.local.room.dao

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.LinuxTestModules
import com.mochame.app.di.TestTag
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKermitApi::class)
class JvmBioDaoTest : BaseBioDaoTest() {
    override val platformTestModules = listOf(
        LinuxTestModules.databaseModule,
        CoreTestModules.testLoggingModule(TestTag.LINUX_X64)
    )
}