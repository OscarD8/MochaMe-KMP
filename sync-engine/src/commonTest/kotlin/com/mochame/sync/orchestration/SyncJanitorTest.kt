package com.mochame.sync.orchestration

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.sync.di.JanitorTestEnvironment
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse


@OptIn(ExperimentalKermitApi::class)
val janitorTestModule = module {
    factoryOf(::JanitorTestEnvironment)
}

class SyncJanitorTest {

    @Test
    fun no_way_this_can_work() {
        val uhoh = false

        assertFalse(uhoh)
    }

}