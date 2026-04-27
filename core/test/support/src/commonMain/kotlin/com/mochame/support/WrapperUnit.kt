package com.mochame.support

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

/**
 * Establishes a pure logic environment.
 * Ties all CoroutineContexts to the virtual clock of runTest.
 */
inline fun <reified E : Any> runUnitEnvironment(
    crossinline koinSetup: KoinApplication.() -> Unit = {},
    crossinline block: suspend E.(TestScope) -> Unit
) = runTest {
    val koinApp = koinApplication {
        allowOverride(true)
        modules(utilizeTestScope())
        koinSetup()
    }

    val environment = koinApp.koin.get<E>()

    try {
        environment.block(this)
    } finally {
        koinApp.close()
    }
}