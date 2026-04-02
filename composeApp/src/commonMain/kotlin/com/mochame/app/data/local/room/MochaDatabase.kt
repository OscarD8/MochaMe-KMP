package com.mochame.app.data.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mochame.app.data.local.room.dao.BioDao
import com.mochame.app.data.local.room.dao.ResonanceDao
import com.mochame.app.data.local.room.dao.TelemetryDao
import com.mochame.app.data.local.room.converter.MochaConverters
import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
import com.mochame.app.data.local.room.dao.SettingsDao
import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
import com.mochame.app.data.local.room.dao.sync.SyncTombstoneDao
import com.mochame.app.data.local.room.entity.AuthorEntity
import com.mochame.app.data.local.room.entity.BookEntity
import com.mochame.app.data.local.room.entity.DailyContextEntity
import com.mochame.app.data.local.room.entity.DomainEntity
import com.mochame.app.data.local.room.entity.GlobalSettingsEntity
import com.mochame.app.data.local.room.entity.MomentEntity
import com.mochame.app.data.local.room.entity.MutationLedgerEntity
import com.mochame.app.data.local.room.entity.QuoteEntity
import com.mochame.app.data.local.room.entity.SpaceEntity
import com.mochame.app.data.local.room.entity.SyncMetadataEntity
import com.mochame.app.data.local.room.entity.SyncTombstoneEntity
import com.mochame.app.data.local.room.entity.TopicEntity
import com.mochame.app.di.providers.DispatcherProvider

@ConstructedBy(MochaDatabaseConstructor::class)
@Database(
    entities = [
        // BIO MODULE
        DailyContextEntity::class,

        // TELEMETRY MODULE
        MomentEntity::class,
        DomainEntity::class,
        TopicEntity::class,
        SpaceEntity::class,

        // SIGNAL MODULE
        AuthorEntity::class,
        BookEntity::class,
        QuoteEntity::class,

        // SYNC HANDLING
        SyncTombstoneEntity::class,
        SyncMetadataEntity::class,
        MutationLedgerEntity::class,

        GlobalSettingsEntity::class
    ],
    version = 10,
    exportSchema = false // Standard for Phase 1 local-only development
)

@TypeConverters(MochaConverters::class)
abstract class MochaDatabase : RoomDatabase() {

    abstract fun bioDao(): BioDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun signalDao(): ResonanceDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun mutationLedgerDao(): MutationLedgerDao
    abstract fun settingsDao(): SettingsDao
}

// Defining the override satisfies the compiler's interface check.
// We use the @Suppress ONLY because Android Studio's editor
// can't "see" the KSP-generated file, even when the build works.

expect object MochaDatabaseConstructor : RoomDatabaseConstructor<MochaDatabase> {
    override fun initialize(): MochaDatabase
}

/**
 * A helper function to create the database builder.
 * This will be called by the platform-specific drivers.
 */
fun getRoomDatabase(
    builder: RoomDatabase.Builder<MochaDatabase>,
    dispatcherProvider: DispatcherProvider
): MochaDatabase {
    return builder
        .setDriver(BundledSQLiteDriver()) // <--- the magic for 2026 parity
//        .enableMultiInstanceInvalidation() Possible consideration for local first implementation
        .setQueryCoroutineContext(dispatcherProvider.io)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA busy_timeout = 500;")
            }
        })
        .build()
}