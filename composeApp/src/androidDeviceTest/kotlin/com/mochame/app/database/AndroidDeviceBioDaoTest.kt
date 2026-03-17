package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.di.DispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class AndroidDeviceBioDaoTest : BaseBioDaoTest() {

    override val platformTestModule = module {

//        single<TestDispatcher> { StandardTestDispatcher() }

        single<MochaDatabase> {
            val testDispatcher = get<TestDispatcher>()

            Room.inMemoryDatabaseBuilder<MochaDatabase>(
                InstrumentationRegistry.getInstrumentation().context
            )
                .setQueryCoroutineContext(testDispatcher)
                .setDriver(BundledSQLiteDriver())
                .build()
        }

        single { get<MochaDatabase>().bioDao() }

        single { get<DateTimeUtils>() }

    }
}