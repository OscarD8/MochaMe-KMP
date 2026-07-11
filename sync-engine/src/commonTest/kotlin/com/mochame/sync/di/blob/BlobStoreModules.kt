package com.mochame.sync.di.blob

import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.utils.fixtures.di.FakeClockModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        FakeClockModule::class,
        FixturesPlatformModule::class,
    ]
)
internal class SyncBlobStoreTestModule
