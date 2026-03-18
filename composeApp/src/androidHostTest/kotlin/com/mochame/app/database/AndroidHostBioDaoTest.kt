package com.mochame.app.database


import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mochame.app.core.DateTimeUtils
import kotlinx.coroutines.test.TestDispatcher
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidHostBioDaoTest : BaseBioDaoTest() {
    override val platformTestModule = module {

        single<MochaDatabase> { params ->
            val testDispatcher: TestDispatcher = params.get()

            Room.inMemoryDatabaseBuilder<MochaDatabase>(
                context = ApplicationProvider.getApplicationContext(),
            )
                .setQueryCoroutineContext(testDispatcher)
                .build()
        }

        single { get<MochaDatabase>().bioDao() }

        single { get<DateTimeUtils>() }

    }
}