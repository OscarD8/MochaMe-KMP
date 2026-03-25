package com.mochame.app.di.modules

import com.mochame.app.database.getDatabaseBuilder // The Android-only path finder
import com.mochame.app.data.local.room.getRoomDatabase    // The Shared configuration
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.di.providers.DispatcherProvider
import kotlinx.coroutines.Dispatchers
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
    single<DispatcherProvider> {
        object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val unconfined = Dispatchers.Unconfined
        }
    }
}