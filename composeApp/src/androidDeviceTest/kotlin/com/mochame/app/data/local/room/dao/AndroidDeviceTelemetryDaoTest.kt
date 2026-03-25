package com.mochame.app.data.local.room.dao

//
//@RunWith(AndroidJUnit4::class)
//class AndroidDeviceTelemetryDaoTest : BaseTelemetryDaoTest() {
//    override val platformTestModule = module {
//
//        single<MochaDatabase> { params ->
//            val testDispatcher: TestDispatcher = params.get()
//
//            Room.inMemoryDatabaseBuilder<MochaDatabase>(
//                InstrumentationRegistry.getInstrumentation().context
//            )
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