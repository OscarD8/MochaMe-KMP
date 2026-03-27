package com.mochame.app.di.modules

import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.getRoomDatabase
import com.mochame.app.database.getDatabaseBuilder
import com.mochame.app.di.providers.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

actual val platformModule = module {
    single<MochaDatabase> {
        // We get the builder from JVM, then finish it in COMMON
        getRoomDatabase(
            getDatabaseBuilder(),
            dispatcherProvider = get()
        )
    }

    single<DispatcherProvider> {
        object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val unconfined = Dispatchers.Unconfined
        }
    }

}