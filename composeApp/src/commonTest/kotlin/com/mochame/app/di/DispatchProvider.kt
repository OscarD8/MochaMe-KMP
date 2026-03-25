package com.mochame.app.di

import com.mochame.app.di.providers.DispatcherProvider
import kotlinx.coroutines.test.TestDispatcher

class TestDispatcherProvider(val testDispatcher: TestDispatcher) : DispatcherProvider {
    override val main = testDispatcher
    override val io = testDispatcher
    override val unconfined = testDispatcher
}