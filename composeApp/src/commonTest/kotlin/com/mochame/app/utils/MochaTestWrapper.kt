package com.mochame.app.utils

import com.mochame.app.di.TestDispatcherProvider
import com.mochame.app.di.providers.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.ContinuationInterceptor


/**
 * Call this inside any runTest block to
 * link the coroutine world to the Koin world.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.establishTestScope(): TestDispatcher {
    val testDispatcher = this.coroutineContext[ContinuationInterceptor.Key] as TestDispatcher

    loadKoinModules(module {
        // Now anyone calling get<TestDispatcher>() gets THIS specific one
        single<TestDispatcher> { testDispatcher }
        factory<DispatcherProvider> { TestDispatcherProvider(get()) }
        factory<CoroutineScope>(named("AppScope")) { this@establishTestScope }
    })

    return testDispatcher
}

//@ExperimentalKermitApi
//interface TestEnvironment {
//    val writer: TestLogWriter
//    fun teardown()
//}
//
///**
// * Global test helper located in commonTest/InternalTestUtils.kt
// */
//@OptIn(ExperimentalKermitApi::class)
//inline fun <reified T : TestEnvironment> KoinTest.runScopeMochaTest(
//    crossinline block: suspend TestScope.(T) -> Unit
//) = runTest {
//    val testDispatcher = this.coroutineContext[ContinuationInterceptor.Key] as TestDispatcher
//
//    val dynamicModule = module {
//        single<TestDispatcher> { testDispatcher }
//        factory<DispatcherProvider> { TestDispatcherProvider(get()) }
//        factory<CoroutineScope>(named("AppScope")) { this@runTest }
//    }
//
//    loadKoinModules(dynamicModule)
//
//    // Initialize as null so we only teardown if it was successfully resolved
//    var resolvedEnv: T? = null
//
//    try {
//        resolvedEnv = get<T>()
//        this.block(resolvedEnv)
//    } finally {
//        resolvedEnv?.teardown()
//        resolvedEnv?.writer?.reset()
//        unloadKoinModules(dynamicModule)
//    }
//}

