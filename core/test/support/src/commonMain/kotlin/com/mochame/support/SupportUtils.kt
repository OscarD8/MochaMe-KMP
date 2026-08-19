package com.mochame.support

import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import com.mochame.annotations.AppScope
import com.mochame.annotations.DefaultContext
import com.mochame.annotations.IoContext
import com.mochame.annotations.MainContext
import com.mochame.logger.test.TestLoggerModule
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

// -----------------------------------------------------------
// MODULES / PLATFORM BRIDGES
// -----------------------------------------------------------

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
    includes = [
        TestLoggerModule::class,
        TestTargetsProviderModule::class
    ]
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
    val dispatcher = this.coroutineContext[ContinuationInterceptor]
        ?: throw IllegalStateException("Error fetching the dispatcher of an established test scope.")

    return module {
        single<CoroutineContext> { dispatcher }
        single<CoroutineContext>(qualifier<IoContext>()) { dispatcher }
        single<CoroutineContext>(qualifier<MainContext>()) { dispatcher }
        single<CoroutineContext>(qualifier<DefaultContext>()) { dispatcher }

        single<CoroutineScope>(qualifier<AppScope>()) { this@scopeKoinModule }
    }
}

// -----------------------------------------------------------
// EXTENSION FUNCTIONS / SETUP UTILITIES
// -----------------------------------------------------------

suspend fun RoomDatabase.getPhysicalRowCount(tableName: String): Int =
    useReaderConnection { connection ->
        connection.usePrepared("SELECT COUNT(*) FROM $tableName") { statement ->
            if (statement.step()) statement.getLong(0).toInt() else 0
        }
    }

fun Exception.reportAndThrowFailure(): Nothing {

    println("\n === POSSIBLE DI REGISTRY ISSUE OR GENERAL FAILURE === ")
    println("Crash: ${this.message}")

    var currentCause = this.cause
    while (currentCause != null) {
        println("If Missing Component Or Error: ${currentCause.message}")
        currentCause = currentCause.cause
    }

    println(" ====================================================== \n")
    throw this
}

fun interface TestTeardownHook {
    fun onTeardown()
}