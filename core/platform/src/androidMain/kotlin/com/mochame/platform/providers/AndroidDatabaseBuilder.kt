package com.mochame.platform.providers

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import com.mochame.platform.di.PlatformContext
import kotlin.coroutines.CoroutineContext

actual inline fun <reified T : RoomDatabase> platformBuilder(
    context: PlatformContext,
    queryContext: CoroutineContext,
    isTest: Boolean,
    location: DatabaseLocation,
    driver: SQLiteDriver,
    noinline factory: () -> T
): RoomDatabase.Builder<T> {
    val builder = when (location) {
        is DatabaseLocation.InMemory -> {
            Room.inMemoryDatabaseBuilder<T>(context.androidContext, factory)
        }

        is DatabaseLocation.OnDisk -> {
            Room.databaseBuilder<T>(context.androidContext, location.path, factory)
        }
    }
    return builder.applyMochaDefaults(queryContext, driver)
}
