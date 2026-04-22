package com.mochame.support

import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteDriver
import com.mochame.platform.providers.PlatformContext
import com.mochame.platform.providers.platformBuilder
import com.mochame.di.AppScope
import com.mochame.di.DefaultContext
import com.mochame.di.IoContext
import com.mochame.di.MainContext
import com.mochame.platform.providers.RoomImmediateTransProvider
import com.mochame.platform.providers.TransactionProvider
import come.mochame.utils.test.di.testLoggingModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
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
inline fun <reified T : RoomDatabase, reified env : Any> runTestWithPersistence(
    constructor: RoomDatabaseConstructor<T>,
    callerModules: List<Module> = emptyList(),
    crossinline daoWiring: T.() -> Module = { module { } },
    crossinline block: suspend env.(T, TestScope) -> Unit
) = runTest {
    val context = this.coroutineContext[ContinuationInterceptor.Key] as CoroutineContext
    val contextModule = this.utilizeTestScope(context)
    val platformModule = getPlatformTestDependencies(context)

    startKoin {
        allowOverride(true)
        modules(contextModule + platformModule + testLoggingModule())
    }

    val dbModule = module {
        single<T> {
            platformBuilder<T>(
                context = get<PlatformContext>(),
                queryContext = get(qualifier<IoContext>()),
                isTest = true,
                path = null,
                driver = get<SQLiteDriver>(),
                factory = { constructor.initialize() }
            ).build()
        }
        single<TransactionProvider> {
            RoomImmediateTransProvider(get<T>())
        }
    }
    loadKoinModules(dbModule)

    val db = getKoin().get<T>(T::class)
    loadKoinModules(db.daoWiring())

    loadKoinModules(callerModules)

    val environment = getKoin().get<env>()

    try {
        environment.block(db, this)
    } finally {
        db.close()
        stopKoin()
    }
}

expect fun getPlatformTestDependencies(testContext: CoroutineContext): Module

/**
 * Allocates all execution contexts to the context of the current test dispatcher.
 * An annotation for AppScope is overridden with the same test scope.
 *
 * @return [Module] having wired the context from this [TestScope]
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.utilizeTestScope(testContext: CoroutineContext): Module {
    return module {
        single<CoroutineContext> { testContext }
        factory<CoroutineScope>(qualifier<AppScope>()) { this@utilizeTestScope }
        single<CoroutineContext>(qualifier<IoContext>()) { testContext }
        single<CoroutineContext>(qualifier<MainContext>()) { testContext }
        single<CoroutineContext>(qualifier<DefaultContext>()) { testContext }
    }
}