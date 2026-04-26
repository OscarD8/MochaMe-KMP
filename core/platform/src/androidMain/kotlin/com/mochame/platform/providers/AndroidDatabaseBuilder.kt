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
    path: AppPathsProvider?,
    driver: SQLiteDriver,
    noinline factory: () -> T
): RoomDatabase.Builder<T> {
    val builder = if (isTest) {
        Room.inMemoryDatabaseBuilder(context.androidContext, factory)
    } else {
        val dbPath = requireNotNull(path?.databasePath) {
            "Build Error: Production Database requested but AppPathsProvider (path) is null."
        }
        Room.databaseBuilder<T>(context.androidContext, path.databasePath, factory)
    }
    return builder.applyMochaDefaults(queryContext, driver)
}
