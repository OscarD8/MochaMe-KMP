
package com.mochame.app.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.app.data.local.room.MochaDbOld
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module

@ExperimentalKermitApi
object AndroidHostTestModules {

    val databaseModule = module {
        single<MochaDbOld> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDbOld>(
                context = ApplicationProvider.getApplicationContext(),
            )
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