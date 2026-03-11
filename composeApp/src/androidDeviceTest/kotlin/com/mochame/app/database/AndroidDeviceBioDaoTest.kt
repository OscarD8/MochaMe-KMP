package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import org.koin.core.module.Module
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class AndroidDeviceBioDaoTest : BaseBioDaoTest() {

    override val platformTestModule = module {
        single<MochaDatabase> {
            Room.inMemoryDatabaseBuilder<MochaDatabase>(
                InstrumentationRegistry.getInstrumentation().context
            )
                .allowMainThreadQueries()
                .setDriver(BundledSQLiteDriver())
                .build()
        }
        single { get<MochaDatabase>().bioDao() }
    }
}