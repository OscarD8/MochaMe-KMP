package com.mochame.app.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.mochame.app.data.local.room.MochaDatabase
import java.io.File

fun getDatabaseBuilder(): RoomDatabase.Builder<MochaDatabase> {
    val userHome = System.getProperty("user.home")
    val dbFile = File(userHome, ".local/share/mochame/mocha_me.db")

    // We only create the BUILDER here. We don't build it yet.
    return Room.databaseBuilder<MochaDatabase>(
        name = dbFile.absolutePath
    )
}