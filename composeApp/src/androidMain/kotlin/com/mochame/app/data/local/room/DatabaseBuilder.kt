package com.mochame.app.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mochame.app.di.providers.AppPaths

/**
 * Android Implementation:
 * Uses the Context to resolve the internal app storage path.
 */
fun getDatabaseBuilder(
    ctx: Context,
    paths: AppPaths
): RoomDatabase.Builder<MochaDatabase> {
    val appContext = ctx.applicationContext

    return Room.databaseBuilder<MochaDatabase>(
        context = appContext,
        name = paths.databasePath
    )
}