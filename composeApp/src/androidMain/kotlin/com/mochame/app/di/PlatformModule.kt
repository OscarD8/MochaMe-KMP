package com.mochame.app.di

import com.mochame.app.database.getDatabaseBuilder // The Android-only path finder
import com.mochame.app.database.getRoomDatabase    // The Shared configuration
import com.mochame.app.database.MochaDatabase
import org.koin.dsl.module

actual val platformModule = module {
    /**
     * THE DATABASE (ANDROID)
     * This defines how to grow the "Database Limb" on the Android planet.
     */
    single<MochaDatabase> {
        // 'get()' tells Koin: "Go into your safe and find that Security Key (Context)
        // we saved during the Ribosome briefing in MainActivity".
        val androidBuilder = getDatabaseBuilder(get())

        // Use the key to find the path, then build the binary heart.
        getRoomDatabase(androidBuilder)
    }
}