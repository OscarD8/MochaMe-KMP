package com.mochame.app.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.app.data.bio.BioDao
import com.mochame.app.database.entities.CategoryEntity
import com.mochame.app.database.entities.DailyContextEntity
import com.mochame.app.data.telemetry.TelemetryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        DailyContextEntity::class,
        CategoryEntity::class,
    ],
    version = 1
)
@ConstructedBy(MochaDatabaseConstructor::class)
abstract class MochaDatabase : RoomDatabase() {
    abstract fun bioDao(): BioDao
    abstract fun telemetryDao(): TelemetryDao
}

// Defining the override satisfies the compiler's interface check.
// We use the @Suppress ONLY because Android Studio's editor
// can't "see" the KSP-generated file, even when the build works.
@Suppress("KotlinNoActualForExpect")
expect object MochaDatabaseConstructor : RoomDatabaseConstructor<MochaDatabase> {
    override fun initialize(): MochaDatabase
}
/**
 * A helper function to create the database builder.
 * This will be called by the platform-specific drivers.
 */
fun getRoomDatabase(
    builder: RoomDatabase.Builder<MochaDatabase>
): MochaDatabase {
    return builder
        .setDriver(BundledSQLiteDriver()) // <--- THIS is the magic for 2026 parity
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}