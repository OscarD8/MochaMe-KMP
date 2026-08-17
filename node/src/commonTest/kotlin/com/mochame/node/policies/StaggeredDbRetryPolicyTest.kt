@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.node.policies

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.node.di.StaggeredDbPolicyTestApp
import com.mochame.node.di.StaggeredDbPolicyTestEnv
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertNotNull

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend StaggeredDbPolicyTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<StaggeredDbPolicyTestEnv>(
        koinSetup = { includes(koinConfiguration<StaggeredDbPolicyTestApp>()) },
        block = block
    )



class StaggeredDbRetryPolicyTest : MochaPlatformTest() {

    @Test
    fun should_retryFourTimes_then_successfullyExecuteBlock() = runEnv {
        assertNotNull(executor)
    }

}

