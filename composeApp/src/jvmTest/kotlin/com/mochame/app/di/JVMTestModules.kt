package com.mochame.app.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.core.CleanLogWriter
import com.mochame.app.database.MochaDatabase
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module

object JVMTestModules {
    val databaseModule = module {
        single<MochaDatabase> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(testDispatcher)
                .build()
        }
    }

    @OptIn(ExperimentalKermitApi::class)
    val loggerModule = module {
        single<Severity> { Severity.Verbose }
        single<TestLogWriter> { TestLogWriter(Severity.Verbose) }
        single<Logger> {
            Logger(
                config = StaticConfig(
                    logWriterList = listOf(
                        get<TestLogWriter>(),
                        CleanLogWriter()
                    )
                ),
                tag = "JVM-Test"
            )
        }
    }

}