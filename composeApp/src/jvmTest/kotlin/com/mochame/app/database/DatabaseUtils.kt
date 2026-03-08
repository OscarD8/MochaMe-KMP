package com.mochame.app.database

import androidx.room.Room
import androidx.room.RoomDatabase

actual fun getTestDatabaseBuilder(): RoomDatabase.Builder<MochaDatabase> {
    return Room.inMemoryDatabaseBuilder<MochaDatabase>()
}
