package com.mochame.schema

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.mochame.schema.MochaConverters
import com.mochame.app.data.local.room.dao.BioDao
import com.mochame.app.data.local.room.dao.ResonanceDao
import com.mochame.app.data.local.room.dao.TelemetryDao
import com.mochame.app.data.local.room.entities.AuthorEntity
import com.mochame.app.data.local.room.entities.BookEntity
import com.mochame.app.data.local.room.entities.DailyContextEntity
import com.mochame.app.data.local.room.entities.DomainEntity
import com.mochame.app.data.local.room.entities.MomentAttachmentEntity
import com.mochame.app.data.local.room.entities.MomentEntity
import com.mochame.app.data.local.room.entities.QuoteEntity
import com.mochame.app.data.local.room.entities.SpaceEntity
import com.mochame.app.data.local.room.entities.TopicEntity
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SettingsDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.data.entities.GlobalSettingsEntity
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.entities.SyncMetadataEntity
import org.koin.dsl.module

@ConstructedBy(MochaDatabaseConstructor::class)
@Database(
    entities = [
        // BIO MODULE
        DailyContextEntity::class,

        // TELEMETRY MODULE
        MomentEntity::class,
        MomentAttachmentEntity::class,
        DomainEntity::class,
        TopicEntity::class,
        SpaceEntity::class,

        // SIGNAL MODULE
        AuthorEntity::class,
        BookEntity::class,
        QuoteEntity::class,

        // SYNC HANDLING
        SyncMetadataEntity::class,
        SyncIntentEntity::class,

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
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun mutationLedgerDao(): MutationLedgerDao
    abstract fun settingsDao(): SettingsDao
}

expect object MochaDatabaseConstructor : RoomDatabaseConstructor<MochaDatabase> {
    override fun initialize(): MochaDatabase
}

val schemaModule = module {
    // We use a factory (or single) to extract DAOs from the master database.
    // When the :sync-engine asks for a SyncMetadataDao, Koin will come here,
    // get the MochaDatabase, call .syncMetadataDao(), and hand it over.

    single<BioDao> { get<MochaDatabase>().bioDao() }
    single<TelemetryDao> { get<MochaDatabase>().telemetryDao() }
    single<SyncMetadataDao> { get<MochaDatabase>().syncMetadataDao() }

}