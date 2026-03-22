package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import com.mochame.app.core.DateTimeUtils
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module


class JvmBioDaoTest : BaseBioDaoTest() {
    override val platformTestModule = module {

        single<MochaDatabase> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(testDispatcher)
                .build()
        }

        single {
            val config = StaticConfig(
                logWriterList = listOf(platformLogWriter())
            )
            Logger(config = config, tag = "AppTag")
        }

        single { get<MochaDatabase>().bioDao() }

        single { get<DateTimeUtils>() }

    }
}