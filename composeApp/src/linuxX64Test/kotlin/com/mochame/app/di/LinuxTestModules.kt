package com.mochame.app.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mochame.app.data.local.room.MochaDatabase
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module

object LinuxTestModules {
    val databaseModule = module {
        single<MochaDatabase> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDatabase>()
                .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())
                .setQueryCoroutineContext(testDispatcher)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(connection: SQLiteConnection) {
                        connection.execSQL("PRAGMA busy_timeout = 5000;")
                    }
                })
                .build()
        }
    }
}