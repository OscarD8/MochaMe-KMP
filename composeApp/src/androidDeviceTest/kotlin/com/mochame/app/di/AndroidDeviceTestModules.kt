@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.app.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import co.touchlab.kermit.ExperimentalKermitApi
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module

object AndroidDeviceTestModules {
    val databaseModule = module {
        single<MochaDbOld> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDbOld>(
                InstrumentationRegistry.getInstrumentation().context
            )
                .setQueryCoroutineContext(testDispatcher)
                .setDriver(BundledSQLiteDriver())
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(connection: SQLiteConnection) {
                        connection.execSQL("PRAGMA busy_timeout = 5000;")
                    }
                })
                .build()
        }
    }
}