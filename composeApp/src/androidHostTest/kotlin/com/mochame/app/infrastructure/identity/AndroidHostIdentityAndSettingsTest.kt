package com.mochame.app.infrastructure.identity

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.AndroidHostTestModules
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalKermitApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidHostIdentityAndSettingsTest : BaseIdentityAndSettingsTest() {
    override val platformModules = listOf(
        AndroidHostTestModules.databaseModule
    )
}