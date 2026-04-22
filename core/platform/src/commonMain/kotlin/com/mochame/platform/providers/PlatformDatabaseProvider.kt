package com.mochame.platform.providers

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.coroutines.CoroutineContext

// This is the "Ghost." Common code can pass it around,
// but it cannot call any methods on it.
expect abstract class PlatformContext

// When you ask for a platformBuilder, the actuals are called
// this passes any potential context and
expect inline fun <reified T : RoomDatabase> platformBuilder(
    context: PlatformContext,
    queryContext: CoroutineContext,
    isTest: Boolean = false,
    path: AppPathsProvider?,
    driver: SQLiteDriver = BundledSQLiteDriver(),
    noinline factory: () -> T
): RoomDatabase.Builder<T>


fun <T : RoomDatabase> RoomDatabase.Builder<T>.applyMochaDefaults(
    queryContext: CoroutineContext,
    driver: SQLiteDriver
): RoomDatabase.Builder<T> {
    return this
        .setDriver(driver)
        .setQueryCoroutineContext(queryContext)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA busy_timeout = 500;")
            }
        })
}