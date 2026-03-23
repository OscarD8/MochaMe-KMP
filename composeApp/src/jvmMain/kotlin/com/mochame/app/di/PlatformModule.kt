package com.mochame.app.di

import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.getDatabaseBuilder // THIS SHOULD NOW RESOLVE
import com.mochame.app.database.getRoomDatabase    // FROM COMMON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual val platformModule = module {
    single<MochaDatabase> {
        // We get the builder from JVM, then finish it in COMMON
        getRoomDatabase(getDatabaseBuilder())
    }
    single<DispatcherProvider> {
        object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val unconfined = Dispatchers.Unconfined
        }
    }

    single(named("AppScope")) {
        val dispatchers = get<DispatcherProvider>()
        CoroutineScope(SupervisorJob() + dispatchers.main)
    }
}