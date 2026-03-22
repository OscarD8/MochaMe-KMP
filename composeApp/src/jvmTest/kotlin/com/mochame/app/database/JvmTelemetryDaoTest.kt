package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.database.triggers.SYNC_TRIGGER_CALLBACK
import kotlinx.coroutines.test.TestDispatcher
import org.koin.dsl.module

//
//class JvmTelemetryDaoTest : BaseTelemetryDaoTest() {
//    override val platformTestModule = module {
//
//        single<MochaDatabase> { params ->
//            val testDispatcher: TestDispatcher = params.get()
//
//            Room.inMemoryDatabaseBuilder<MochaDatabase>()
//                .setDriver(BundledSQLiteDriver())
//                .setQueryCoroutineContext(testDispatcher)
//                .addCallback(SYNC_TRIGGER_CALLBACK)
//                .build()
//        }
//
//        single { get<MochaDatabase>().bioDao() }
//
//        single { get<DateTimeUtils>() }
//
//    }
//}