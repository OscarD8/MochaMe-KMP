package com.mochame.support

import com.mochame.di.AppScope
import com.mochame.di.DefaultContext
import com.mochame.di.IoContext
import com.mochame.di.MainContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Central bridge for TestRunners
 */
expect abstract class MochaPlatformTest()


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

