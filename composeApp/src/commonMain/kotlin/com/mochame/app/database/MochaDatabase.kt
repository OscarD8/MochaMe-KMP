package com.mochame.app.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.app.database.dao.BioDao
import com.mochame.app.database.dao.SignalDao
import com.mochame.app.database.dao.TelemetryDao
import com.mochame.app.database.entity.*
import com.mochame.app.database.converter.MochaConverters
import com.mochame.app.database.dao.sync.MutationLedgerDao
import com.mochame.app.database.dao.SettingsDao
import com.mochame.app.database.dao.sync.SyncMetadataDao
import com.mochame.app.database.dao.sync.SyncTombstoneDao
import com.mochame.app.database.triggers.SYNC_TRIGGER_CALLBACK
import kotlinx.coroutines.Dispatchers

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
        MutationEntryEntity::class,

        GlobalSettingsEntity::class
    ],
    version = 10,
    exportSchema = false // Standard for Phase 1 local-only development
)

@TypeConverters(MochaConverters::class)
abstract class MochaDatabase : RoomDatabase() {

    abstract fun bioDao(): BioDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun signalDao(): SignalDao
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
    builder: RoomDatabase.Builder<MochaDatabase>
): MochaDatabase {
    return builder
        .setDriver(BundledSQLiteDriver()) // <--- THIS is the magic for 2026 parity
        .setQueryCoroutineContext(Dispatchers.IO)
//        .enableMultiInstanceInvalidation() Possible consideration for local first implementation
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(SYNC_TRIGGER_CALLBACK)
        .build()
}