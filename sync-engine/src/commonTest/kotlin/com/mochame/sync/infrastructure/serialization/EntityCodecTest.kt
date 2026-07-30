@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.di.codec.EntityCodecTestApp
import com.mochame.sync.di.codec.EntityCodecTestEnv
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertNotNull


// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend EntityCodecTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<EntityCodecTestEnv>(
        koinSetup = { includes(koinConfiguration<EntityCodecTestApp>()) },
        block = block
    )


class EntityCodecTest : MochaPlatformTest() {

    @Test
    fun yay_or_nay() = runEnv {
        assertNotNull(codec)
    }


}