package com.mochame.support

import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteDriver
import com.mochame.core.providers.PlatformContext
import com.mochame.core.providers.platformBuilder
import com.mochame.di.AppScope
import com.mochame.di.DefaultContext
import com.mochame.di.IoContext
import com.mochame.di.MainContext
import com.mochame.support.di.testLoggingModule
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
 * Allocates all execution contexts to the context of the current test dispatcher.
 * An annotation for AppScope is overridden with the same test scope.
 *
 * @return [TestDispatcher] picked from the current test scope
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.utilizeTestScope(): TestDispatcher {
    val testContext = this.coroutineContext[ContinuationInterceptor.Key] as TestDispatcher

    loadKoinModules(module {
        single<CoroutineContext> { testContext }
        factory<CoroutineScope>(qualifier<AppScope>()) { this@utilizeTestScope }
        single<CoroutineContext>(qualifier<IoContext>()) { testContext }
        single<CoroutineContext>(qualifier<MainContext>()) { testContext }
        single<CoroutineContext>(qualifier<DefaultContext>()) { testContext }
    })

    return testContext
}

expect fun getPlatformTestDependencies(testContext: CoroutineContext): Module

/**
 * Wrapper method that calls [platformBuilder] which handles establishing an
 * inMemory database and target dependencies.
 *
 * By calling this method, any scopes and coroutine contexts will be aligned
 * with the current [TestScope], the inMemory database will be established,
 * and the TestScope passed back to the caller for manipulating the virtual
 * clock. The provided environment will be the scope you are now in.
 */
inline fun <reified T : RoomDatabase, reified env : Any> runTestWithPersistence(
    constructor: RoomDatabaseConstructor<T>,
    callerModules: List<Module> = emptyList(),
    crossinline block: suspend env.(T, TestScope) -> Unit
) = runTest {
    val testContext = this.utilizeTestScope()
    val platformModule = getPlatformTestDependencies(testContext)

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
    }

    startKoin {
        allowOverride(true)
        modules(callerModules + platformModule + dbModule + testLoggingModule())
    }

    val koin = getKoin()
    val db = koin.get<T>(T::class)
    val environment = koin.get<env>(env::class)

    try {
        environment.block(db, this)
    } finally {
        db.close()
        stopKoin()
    }
}