package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.app.di.DispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module


class JvmBioDaoTest : BaseBioDaoTest() {
    override val platformTestModule = module {
        single<MochaDatabase> {
            Room.inMemoryDatabaseBuilder<MochaDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
        }
        single { get<MochaDatabase>().bioDao() }

        single<TestDispatcher> { StandardTestDispatcher() }

        single<DispatcherProvider> {
            val sharedClock = get<TestDispatcher>()

            object : DispatcherProvider {
                override val main = sharedClock
                override val io = sharedClock
                override val unconfined = sharedClock
            }
        }
    }
}