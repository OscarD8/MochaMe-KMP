package com.mochame.sync.domain

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.di.domain.PruneEntriesTestEnv
import com.mochame.sync.di.domain.PruneEntriesUseCaseTestApp
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertTrue

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: PruneEntriesTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment (
        koinSetup = { includes(koinConfiguration<PruneEntriesUseCaseTestApp>()) },
        block = block
    )

class PruneEntriesUseCaseTest : MochaPlatformTest() {

    @Test
    fun yay_or_nay() {
        assertTrue(true)
    }
}