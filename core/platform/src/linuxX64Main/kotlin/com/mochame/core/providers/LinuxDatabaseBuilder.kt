package com.mochame.core.providers

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlin.coroutines.CoroutineContext

actual abstract class PlatformContext


actual inline fun <reified T : RoomDatabase> platformBuilder(
    context: PlatformContext,
    queryContext: CoroutineContext,
    isTest: Boolean,
    path: AppPathsProvider?,
    driver: SQLiteDriver,
    noinline factory: () -> T
): RoomDatabase.Builder<T> {
    val builder = if (isTest) {
        Room.inMemoryDatabaseBuilder(factory)
    } else {
        Room.databaseBuilder<T>(name = path!!.databasePath, factory = factory)
    }
    return builder.applyMochaDefaults(queryContext, driver)
}