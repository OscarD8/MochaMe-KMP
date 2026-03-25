package com.mochame.app.data.local.room

//@RunWith(RobolectricTestRunner::class)
//class AndroidHostTelemetryDaoTest : BaseTelemetryDaoTest() {
//    override val platformTestModule = module {
//
//        single<MochaDatabase> { params ->
//            val testDispatcher: TestDispatcher = params.get()
//
//            Room.inMemoryDatabaseBuilder<MochaDatabase>(
//                context = ApplicationProvider.getApplicationContext(),
//            )
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