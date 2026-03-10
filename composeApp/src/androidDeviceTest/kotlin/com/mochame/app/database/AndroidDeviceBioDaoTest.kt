package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class AndroidDeviceBioDaoTest : SharedBioDaoTest() {

    override fun createDatabase(): MochaDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, MochaDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
    }
}