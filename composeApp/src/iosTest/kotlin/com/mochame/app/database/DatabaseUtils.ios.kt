package com.mochame.app.database

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

// In iosMain
actual fun getTestDatabaseBuilder(): RoomDatabase.Builder<MochaDatabase> {
    val path = NSHomeDirectory() + "/test.db"
    return Room.databaseBuilder<MochaDatabase>(name = path)
}
