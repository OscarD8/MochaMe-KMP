package com.mochame.support

import com.mochame.contract.di.AppScope
import com.mochame.contract.di.DefaultContext
import com.mochame.contract.di.IoContext
import com.mochame.contract.di.MainContext
import com.mochame.logger.test.TestLoggerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import org.koin.core.annotation.Single
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Central bridge for TestRunners
 */
expect abstract class MochaPlatformTest()


/**
 * Provides platform test dependencies.
 */
@org.koin.core.annotation.Module
expect class TestDependenciesModule()


/**
 * Links the current test [kotlin.coroutines.coroutineContext] from the [TestScope]
 * to qualifiers associated with coroutine context in the SUT.
 *
 * @return [Module] wiring the context from this [TestScope]
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.utilizeTestScope(): Module {
    val testContext =
        this.coroutineContext[ContinuationInterceptor.Key] as CoroutineContext

    return module {
        single<CoroutineContext> { testContext }
        factory<CoroutineScope>(qualifier<AppScope>()) { this@utilizeTestScope }
        single<CoroutineContext>(qualifier<IoContext>()) { testContext }
        single<CoroutineContext>(qualifier<MainContext>()) { testContext }
        single<CoroutineContext>(qualifier<DefaultContext>()) { testContext }
    }
}


/**
 * Global providers for testing setup.
 */
@org.koin.core.annotation.Module([TestLoggerModule::class, TestDependenciesModule::class])
class TestSupportModule {
    @Single
    @AppScope
    fun provideTestAppScope(): CoroutineScope = CoroutineScope(SupervisorJob())

    @Single
    @IoContext
    fun provideTestIoContext(): CoroutineContext = EmptyCoroutineContext
}


/**
 * Forgive the format. It's really helpful.
 */
fun Exception.reportAndThrowDiFailure(): Nothing {
    println("\n🚨 === DI REGISTRY FAILURE === 🚨")
    println("Crash: ${this.message}")

    var currentCause = this.cause
    while (currentCause != null) {
        println("Missing Component: ${currentCause.message}")
        currentCause = currentCause.cause
    }

    println("🚨 =========================== 🚨\n")
    throw this
}