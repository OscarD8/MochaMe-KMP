package com.mochame.support

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.koin.core.KoinApplication
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.koinApplication

/**
 * Establishes a pure logic environment, and handles Koin App lifecycle.
 * Ties all CoroutineContexts to the virtual clock of runTest.
 */
@OptIn(KoinInternalApi::class)
inline fun <reified E : Any> runUnitEnvironment(
    crossinline koinSetup: KoinApplication.() -> Unit = {},
    crossinline block: suspend E.(TestScope) -> Unit
) = runTest {
    val koinApp = koinApplication(createEagerInstances = false) {
        allowOverride(true)
        modules(scopeKoinModule())
        koinSetup()
    }

    val koin = koinApp.koin

    try {
        val environment = koin.get<E>()
        environment.block(this)
    } catch (e: Exception) {
        e.reportAndThrowFailure()
    } finally {
        koinApp.close()
    }
}