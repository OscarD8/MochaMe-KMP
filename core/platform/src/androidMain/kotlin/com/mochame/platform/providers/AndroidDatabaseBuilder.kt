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
        Room.databaseBuilder<T>(context.androidContext, path!!.databasePath, factory)
    }
    return builder.applyMochaDefaults(queryContext, driver)
}

//actual class AndroidEnvironment(private val context: Context) : DatabaseEnvironment {
//    override fun <T : RoomDatabase> build(
//        create: (Context) -> RoomDatabase.Builder<T>
//    ): T {
//        // 1. The feature module uses the context to call the REIFIED builder
//        val builder = create(context)
//
//        // 2. The platform module applies the shared, complex configuration
//        return builder
//            .setDriver(BundledSQLiteDriver())
//            .setQueryCoroutineContext(Dispatchers.IO)
//            .fallbackToDestructiveMigration(dropAllTables = true)
//            .build()
//    }
//}