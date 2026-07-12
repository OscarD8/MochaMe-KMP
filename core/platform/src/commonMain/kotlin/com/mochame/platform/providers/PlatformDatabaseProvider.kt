package com.mochame.platform.providers

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mochame.platform.di.PlatformContext
import kotlin.coroutines.CoroutineContext


expect inline fun <reified T : RoomDatabase> platformBuilder(
    context: PlatformContext,
    queryContext: CoroutineContext,
    isTest: Boolean = false,
    location: DatabaseLocation,
    driver: SQLiteDriver = BundledSQLiteDriver(),
    noinline factory: () -> T
): RoomDatabase.Builder<T>


fun <T : RoomDatabase> RoomDatabase.Builder<T>.applyMochaDefaults(
    queryContext: CoroutineContext,
    driver: SQLiteDriver
): RoomDatabase.Builder<T> = this
    .setDriver(driver)
    .setQueryCoroutineContext(queryContext)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(connection: SQLiteConnection) {
            connection.execSQL("PRAGMA busy_timeout = 500;")
        }
    })
