//package com.mochame.app.data.local.room
//
//import androidx.room.ConstructedBy
//import androidx.room.Database
//import androidx.room.RoomDatabase
//import androidx.room.RoomDatabaseConstructor
//import androidx.room.TypeConverters
//import androidx.sqlite.SQLiteConnection
//import androidx.sqlite.driver.bundled.BundledSQLiteDriver
//import androidx.sqlite.execSQL
//import com.mochame.app.data.local.room.converter.MochaConverters
//import com.mochame.app.data.local.room.dao.BioDao
//import com.mochame.app.data.local.room.dao.ResonanceDao
//import com.mochame.app.data.local.room.dao.SettingsDao
//import com.mochame.app.data.local.room.dao.TelemetryDao
//import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
//import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
//import com.mochame.app.data.local.room.entities.AuthorEntity
//import com.mochame.app.data.local.room.entities.BookEntity
//import com.mochame.app.data.local.room.entities.DailyContextEntity
//import com.mochame.app.data.local.room.entities.DomainEntity
//import com.mochame.app.data.local.room.entities.GlobalSettingsEntity
//import com.mochame.app.data.local.room.entities.MomentAttachmentEntity
//import com.mochame.app.data.local.room.entities.MomentEntity
//import com.mochame.app.data.local.room.entities.QuoteEntity
//import com.mochame.app.data.local.room.entities.SpaceEntity
//import com.mochame.app.data.local.room.entities.SyncIntentEntity
//import com.mochame.app.data.local.room.entities.SyncMetadataEntity
//import com.mochame.app.data.local.room.entities.TopicEntity
//import com.mochame.app.di.providers.DispatcherProvider
//
//@ConstructedBy(MochaDatabaseConstructorOld::class)
//@Database(
//    entities = [
//        // BIO MODULE
//        DailyContextEntity::class,
//
//        // TELEMETRY MODULE
//        MomentEntity::class,
//        MomentAttachmentEntity::class,
//        DomainEntity::class,
//        TopicEntity::class,
//        SpaceEntity::class,
//
//        // SIGNAL MODULE
//        AuthorEntity::class,
//        BookEntity::class,
//        QuoteEntity::class,
//
//        // SYNC HANDLING
//        SyncMetadataEntity::class,
//        SyncIntentEntity::class,
//
//        GlobalSettingsEntity::class
//    ],
//    version = 10,
//    exportSchema = false // Standard for Phase 1 local-only development
//)
//@TypeConverters(MochaConverters::class)
//abstract class MochaDbOld : RoomDatabase() {
//
//    abstract fun bioDao(): BioDao
//    abstract fun telemetryDao(): TelemetryDao
//    abstract fun signalDao(): ResonanceDao
//    abstract fun syncMetadataDao(): SyncMetadataDao
//    abstract fun mutationLedgerDao(): MutationLedgerDao
//    abstract fun settingsDao(): SettingsDao
//}
//
//// Defining the override satisfies the compiler's interface check.
//// We use the @Suppress ONLY because Android Studio's editor
//// can't "see" the KSP-generated file, even when the build works.
//
//expect object MochaDatabaseConstructorOld : RoomDatabaseConstructor<MochaDbOld> {
//    override fun initialize(): MochaDbOld
//}
//
///**
// * A helper function to create the database builder.
// * This will be called by the platform-specific drivers.
// */
//fun getRoomDatabase(
//    builder: RoomDatabase.Builder<MochaDbOld>,
//    dispatcherProvider: DispatcherProvider
//): MochaDbOld {
//    return builder
//        .setDriver(BundledSQLiteDriver())
////        .enableMultiInstanceInvalidation() Possible consideration for local first implementation
//        .setQueryCoroutineContext(dispatcherProvider.io)
//        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
//        .fallbackToDestructiveMigration(dropAllTables = true)
//        .addCallback(object : RoomDatabase.Callback() {
//            override fun onOpen(connection: SQLiteConnection) {
//                connection.execSQL("PRAGMA busy_timeout = 500;")
//            }
//        })
//        .build()
//}
//// this is called by the actual class, so the above never happens in isolation,
//// but defines build logic across all platforms that is basically a config