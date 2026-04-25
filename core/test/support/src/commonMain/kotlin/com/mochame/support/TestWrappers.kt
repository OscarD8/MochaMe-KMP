package com.mochame.support

import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.mochame.di.AppScope
import com.mochame.di.DefaultContext
import com.mochame.di.IoContext
import com.mochame.di.MainContext
import com.mochame.platform.providers.RoomImmediateTransProvider
import com.mochame.platform.providers.TransactionProvider
import com.mochame.platform.providers.platformBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * A wrapper method that calls [platformBuilder] which handles establishing a
 * database for production, but this method overrides platform dependencies in :core:platform
 * via an interception through Koin that links test dependencies. isTest is set
 * to true for the database builder, ensuring an inMemory database is built. Koin DSL is
 * used to provide a hybrid approach to dependency injection, allowing the
 * runtime isolated [TestScope] to be fetched and wired into the
 * [androidx.room.RoomDatabase.Builder.setQueryCoroutineContext] of the
 * database builder, and any components requiring a [CoroutineContext] or [CoroutineScope]
 * themselves. This is handled by the nested [utilizeTestScope].
 *
 * By calling this method, any scopes and coroutine contexts will be aligned
 * with the current [TestScope], the inMemory database will be established,
 * and the TestScope provided to the caller for manipulating the virtual
 * clock. The provided environment will be the scope you are now in.
 */
inline fun <reified T : RoomDatabase, reified E : Any> runTestWithPersistence(
    constructor: RoomDatabaseConstructor<T>,
    crossinline koinSetup: KoinApplication.() -> Unit = {},
    crossinline block: suspend E.(TestScope) -> Unit
) = runTest {

    val koinApp = koinApplication(createEagerInstances = false) {
        allowOverride(true)
        koinSetup()
        modules(utilizeTestScope())
    }

    val koin = koinApp.koin

    val database = platformBuilder<T>(
        context = koin.get(),
        queryContext = koin.get(qualifier<IoContext>()),
        isTest = true,
        path = null,
        driver = koin.get(),
        factory = { constructor.initialize() }
    ).build()

    koin.loadModules(
        listOf(
            module {
                single<T> { database }
                single<TransactionProvider> { RoomImmediateTransProvider(get<T>()) }
            },
        )
    )

    try {
        koin.createEagerInstances()
    } catch (e: Exception) {
        reportDiFailureAndThrow(e)
    }

    val environment = koin.get<E>()

    try {
        environment.block(this)
    } finally {
        database.close()
        koinApp.close()
    }
}


@org.koin.core.annotation.Module
expect class TestSupportModule()

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
 * Forgive the format. It's really helpful.
 */
fun reportDiFailureAndThrow(e: Exception): Nothing {
    println("\n🚨 === DI REGISTRY FAILURE === 🚨")
    println("Crash: ${e.message}")

    var cause = e.cause
    while (cause != null) {
        println("Missing Component: ${cause.message}")
        cause = cause.cause
    }

    println("🚨 =========================== 🚨\n")
    throw e
}