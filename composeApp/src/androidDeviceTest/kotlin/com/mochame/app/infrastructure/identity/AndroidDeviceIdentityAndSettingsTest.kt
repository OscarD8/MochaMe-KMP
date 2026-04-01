package com.mochame.app.infrastructure.identity

import androidx.test.ext.junit.runners.AndroidJUnit4
import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.di.AndroidDeviceTestModules
import org.junit.runner.RunWith

@OptIn(ExperimentalKermitApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidDeviceIdentityAndSettingsTest : BaseIdentityAndSettingsTest() {
    override val platformModules = listOf(
        AndroidDeviceTestModules.databaseModule
    )
}