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
 * Wrapper method that calls [platformBuilder] which handles establishing an
 * inMemory database, with platform dependencies overridden in :core:platform
 * via an interception of this methods [getPlatformTestDependencies] that
 * does what it says purely in relation to database dependencies.
 *
 * By calling this method, any scopes and coroutine contexts will be aligned
 * with the current [TestScope], the inMemory database will be established,
 * passing control back to the caller to handle their DAOs,
 * and the TestScope provided to the caller for manipulating the virtual
 * clock. The provided environment will be the scope you are now in.
 *
 * The idea is to iterate the koin dependency graph. Order of instantiating
 * for dependency mapping:
 * - Test Context, Platform Dependencies, Logger
 * - Database (accepts updated query context, and platform dependencies)
 * - DAOs (database now initialized)
 * - Caller modules (scopes, contexts and DAOs can now be mapped - protect against
 * production components that declare `createdAtStart`)
 */
inline fun <reified T : RoomDatabase, reified E : Any> runTestWithPersistence(
    constructor: RoomDatabaseConstructor<T>,
    crossinline koinSetup: KoinApplication.() -> Unit = {},
    crossinline daoWiring: T.() -> Module = { module { } },
    crossinline block: suspend E.(TestScope) -> Unit
) = runTest {

    val koinApp = koinApplication(createEagerInstances = false) {
        allowOverride(true)
        koinSetup()
        modules(utilizeTestScope())
    }

    val koin = koinApp.koin

    val db = platformBuilder<T>(
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
                single<T> { db }
                single<TransactionProvider> { RoomImmediateTransProvider(get<T>()) }
            },
            db.daoWiring()
        )
    )

    // Its actually really helpful
    try {
        koin.createEagerInstances()
    } catch (e: Exception) {
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

    val environment = koin.get<E>()

    try {
        environment.block(this)
    } finally {
        db.close()
        koinApp.close()
    }
}


@org.koin.core.annotation.Module
expect class SupportProviderModule()

/**
 * Allocates all execution contexts to the context of the current test dispatcher.
 * An annotation for AppScope is overridden with the same test scope.
 *
 * @return [Module] having wired the context from this [TestScope]
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