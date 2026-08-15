package com.mochame.sync.di.fixtures

import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.sync.di.FakeSyncStoresModule
import com.mochame.sync.di.FakeSyncWorkerHookModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        FixturesPlatformModule::class,
        FakeSyncStoresModule::class,
        FakeSyncWorkerHookModule::class
    ]
)
@ComponentScan("com.mochame.sync.fixtures")
class SyncFixturesModule