package com.mochame.app.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry

// Basically dud for all I can tell?
actual fun getTestDatabaseBuilder(): RoomDatabase.Builder<MochaDatabase> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return Room.inMemoryDatabaseBuilder(context, MochaDatabase::class.java)
        .allowMainThreadQueries()
        .setDriver(BundledSQLiteDriver())
}