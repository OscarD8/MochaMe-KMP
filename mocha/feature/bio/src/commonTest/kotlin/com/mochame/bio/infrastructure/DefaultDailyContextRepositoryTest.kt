package com.mochame.bio.infrastructure

import com.mochame.bio.data.BioMicroSchemaConstructor
import com.mochame.bio.di.BioDaoTestApp
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runPersistenceEnvironment
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertNotNull

private inline fun runEnv(crossinline block: DefaultDailyContextRepository.(TestScope) -> Unit) =
    runPersistenceEnvironment(
        constructor = BioMicroSchemaConstructor,
        koinSetup = { includes(koinConfiguration<BioDaoTestApp>()) },
        block = block
    )


class DefaultDailyContextRepositoryTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // BOOT STATE GUARD
    // -----------------------------------------------------------

    @Test
    fun yay_or_nay() = runEnv {
        assertNotNull(this)
    }

}