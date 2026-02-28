package com.mochame.app.di

import com.mochame.app.database.getDatabaseBuilder // The Android-only path finder
import com.mochame.app.database.getRoomDatabase    // The Shared configuration
import com.mochame.app.database.MochaDatabase
import org.koin.dsl.module

actual val platformModule = module {
    single<MochaDatabase> {
        // 1. Get the Context and create the Android-specific builder
        val androidBuilder = getDatabaseBuilder(get())

        // 2. Pass that builder into the shared config to add the Bundled Driver
        getRoomDatabase(androidBuilder)
    }
}