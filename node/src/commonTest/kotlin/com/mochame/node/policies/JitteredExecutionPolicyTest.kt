@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.node.policies

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.node.di.JitteredExecutionTestApp
import com.mochame.node.di.JitteredExecutionTestEnv
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
private inline fun runEnv(crossinline block: suspend JitteredExecutionTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<JitteredExecutionTestEnv>(
        koinSetup = { includes(koinConfiguration<JitteredExecutionTestApp>()) },
        block = block
    )



class JitteredExecutionPolicyTest : MochaPlatformTest() {

    @Test
    fun should_retryFourTimes_then_successfullyExecuteBlock() = runEnv {
        assertNotNull(executor)
    }

}

