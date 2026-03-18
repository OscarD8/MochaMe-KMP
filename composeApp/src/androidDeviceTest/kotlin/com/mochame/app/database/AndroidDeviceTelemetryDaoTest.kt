package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.database.triggers.SYNC_TRIGGER_CALLBACK
import kotlinx.coroutines.test.TestDispatcher
import org.junit.runner.RunWith
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class AndroidDeviceTelemetryDaoTest : BaseTelemetryDaoTest() {
    override val platformTestModule = module {

        single<MochaDatabase> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDatabase>(
                InstrumentationRegistry.getInstrumentation().context
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(testDispatcher)
                .addCallback(SYNC_TRIGGER_CALLBACK)
                .build()
        }

        single { get<MochaDatabase>().bioDao() }

        single { get<DateTimeUtils>() }

    }
}