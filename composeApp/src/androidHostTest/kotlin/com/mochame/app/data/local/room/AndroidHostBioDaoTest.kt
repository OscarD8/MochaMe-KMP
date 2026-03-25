package com.mochame.app.data.local.room


import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.data.local.room.BaseBioDaoTest
import com.mochame.app.di.AndroidHostTestModules
import com.mochame.app.di.CoreTestModules.testLoggingModule
import com.mochame.app.di.TestTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalKermitApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidHostBioDaoTest : BaseBioDaoTest() {
    override val platformTestModules = listOf(
        AndroidHostTestModules.databaseModule,
        testLoggingModule(TestTag.ANDROIDHOST)
    )

}