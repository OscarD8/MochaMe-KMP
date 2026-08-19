@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.api

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.di.api.LocalFirstRepoTestApp
import com.mochame.sync.di.api.LocalFirstRepoTestEnv
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertNotNull

private inline fun runEnv(crossinline block: LocalFirstRepoTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<LocalFirstRepoTestEnv>(
        koinSetup = { includes(koinConfiguration<LocalFirstRepoTestApp>()) },
        block = block
    )

class LocalFirstRepositoryTest : MochaPlatformTest() {

    @Test
    fun yay_or_nay() = runEnv {
        assertNotNull(repo)
    }


}