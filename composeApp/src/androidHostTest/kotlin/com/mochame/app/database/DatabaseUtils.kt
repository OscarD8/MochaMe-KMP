package com.mochame.app.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider


// Basically dud for all I can tell? I don't expect this to ever be
// used, but it is necessary to enable ios and jvm inMemory calls
actual fun getTestDatabaseBuilder(): RoomDatabase.Builder<MochaDatabase> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, MochaDatabase::class.java)
        .allowMainThreadQueries()
        .setDriver(BundledSQLiteDriver())
}