package com.mochame.sync.di.blob

import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        FakeTimeProviderModule::class,
        FixturesPlatformModule::class,
    ]
)
internal class SyncBlobStoreTestModule
