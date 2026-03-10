package com.mochame.app.di

import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.getDatabaseBuilder
import com.mochame.app.database.getRoomDatabase
import org.koin.dsl.module

actual val platformModule = module {
    single<MochaDatabase> {
        // We get the builder from JVM, then finish it in COMMON
        getRoomDatabase(getDatabaseBuilder())
    }
}