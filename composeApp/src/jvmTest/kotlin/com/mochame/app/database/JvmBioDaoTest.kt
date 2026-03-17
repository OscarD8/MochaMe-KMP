package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.di.DispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module


class JvmBioDaoTest : BaseBioDaoTest() {
    override val platformTestModule = module {

//        single<TestDispatcher> { StandardTestDispatcher() }

        single<MochaDatabase> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(testDispatcher)
                .build()
        }

        single { get<MochaDatabase>().bioDao() }

        single { get<DateTimeUtils>() }

    }
}