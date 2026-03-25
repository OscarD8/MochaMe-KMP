package com.mochame.app.data.local.room.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.data.local.room.BaseBioDaoTest
import com.mochame.app.di.AndroidDeviceTestModules
import com.mochame.app.di.CoreTestModules.testLoggingModule
import com.mochame.app.di.TestTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKermitApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidDeviceBioDaoTest : BaseBioDaoTest() {
    override val platformTestModules = listOf(
        AndroidDeviceTestModules.databaseModule,
        testLoggingModule(TestTag.ANDROIDEVICE)
    )
}