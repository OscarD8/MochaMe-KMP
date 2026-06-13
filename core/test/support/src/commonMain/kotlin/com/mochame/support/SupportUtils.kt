package com.mochame.support

import com.mochame.contract.di.AppScope
import com.mochame.contract.di.DefaultContext
import com.mochame.contract.di.IoContext
import com.mochame.contract.di.MainContext
import com.mochame.logger.test.TestLoggerModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import org.koin.core.annotation.Configuration
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
expect class TestTargetsProviderModule()


/**
 * Global providers for testing setup.
 */
@Configuration
@org.koin.core.annotation.Module(
    includes = [TestLoggerModule::class, TestTargetsProviderModule::class]
)
class TestSupportModule {
    @Single
    @AppScope
    fun provideTestAppScope(): CoroutineScope = CoroutineScope(SupervisorJob())

    @Single
    @IoContext
    fun provideTestIoContext(): CoroutineContext = EmptyCoroutineContext
}


/**
 * Generates the test context bindings dynamically.
 */
fun TestScope.scopeKoinModule(): Module {
    val dispatcher = this.coroutineContext[ContinuationInterceptor.Key]
        ?: throw IllegalStateException("Error fetching the dispatcher of an established test scope.")

    return module {
        single<CoroutineContext> { dispatcher }
        single<CoroutineContext>(qualifier<IoContext>()) { dispatcher }
        single<CoroutineContext>(qualifier<MainContext>()) { dispatcher }
        single<CoroutineContext>(qualifier<DefaultContext>()) { dispatcher }

        factory<CoroutineScope>(qualifier<AppScope>()) { this@scopeKoinModule }
    }
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