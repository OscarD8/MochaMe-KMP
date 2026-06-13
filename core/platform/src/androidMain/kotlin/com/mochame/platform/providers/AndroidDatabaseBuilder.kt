package com.mochame.platform.providers

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import org.koin.core.annotation.Module
import kotlin.coroutines.CoroutineContext

@Module
actual class PlatformContext(val androidContext: Context)

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
