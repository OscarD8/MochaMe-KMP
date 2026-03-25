
package com.mochame.app.di

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.infrastructure.logging.CleanLogWriter
import com.mochame.app.data.local.room.MochaDatabase
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module

@ExperimentalKermitApi
object AndroidHostTestModules {

    val databaseModule = module {
        single<MochaDatabase> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDatabase>(
                context = ApplicationProvider.getApplicationContext(),
            )
                .setQueryCoroutineContext(testDispatcher)
                .build()
        }
    }
}