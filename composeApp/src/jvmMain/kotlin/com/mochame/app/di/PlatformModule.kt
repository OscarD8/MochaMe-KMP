package com.mochame.app.di

import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.getDatabaseBuilder // THIS SHOULD NOW RESOLVE
import com.mochame.app.database.getRoomDatabase    // FROM COMMON
import org.koin.dsl.module

actual val platformModule = module {
    single<MochaDatabase> {
        // We get the builder from JVM, then finish it in COMMON
        getRoomDatabase(getDatabaseBuilder())
    }
}