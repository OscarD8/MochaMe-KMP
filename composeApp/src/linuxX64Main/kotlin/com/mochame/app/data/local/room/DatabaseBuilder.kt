package com.mochame.app.data.local.room

import androidx.room.Room
import androidx.room.RoomDatabase
import com.mochame.app.di.providers.AppPaths

fun getDatabaseBuilder(
    paths: AppPaths
) : RoomDatabase.Builder<MochaDatabase> {

    // path handling
    return Room.databaseBuilder<MochaDatabase>(
        name = paths.databasePath
    )
}

