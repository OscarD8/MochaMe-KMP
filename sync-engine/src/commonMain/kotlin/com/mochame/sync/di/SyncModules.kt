package com.mochame.sync.di

import com.mochame.di.BlobMutex
import com.mochame.di.JanitorMutex
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

@Module
@ComponentScan("com.mochame.sync")
class SyncEngineModule {
    val definitions = module {
        single(qualifier<BlobMutex>()) { Mutex() }
        single(qualifier<JanitorMutex>()) { Mutex() }
    }
}


