package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.dsl.module


class JvmBioDaoTest : BaseBioDaoTest() {
    override val platformTestModule = module {
        single<MochaDatabase> {
            Room.inMemoryDatabaseBuilder<MochaDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
        }
        single { get<MochaDatabase>().bioDao() }
    }
}