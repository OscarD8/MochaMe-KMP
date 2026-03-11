package com.mochame.app.database


import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidBioDaoTest : BaseBioDaoTest() {
    override val platformTestModule = module {
        single<MochaDatabase> {
            Room.inMemoryDatabaseBuilder<MochaDatabase>(
                context = ApplicationProvider.getApplicationContext(),
            ).build()
        }
        single { get<MochaDatabase>().bioDao() }
    }
}