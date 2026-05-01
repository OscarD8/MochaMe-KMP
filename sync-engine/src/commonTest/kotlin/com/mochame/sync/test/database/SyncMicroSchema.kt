package com.mochame.sync.test.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.mochame.sync.data.SyncConverters
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.entities.SyncMetadataEntity
import com.mochame.system.infra.data.NodeContextDao
import com.mochame.system.infra.data.NodeIdentityEntity

@ConstructedBy(SyncMicroSchemaConstructor::class)
@Database(
    entities = [SyncMetadataEntity::class, SyncIntentEntity::class, NodeIdentityEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SyncConverters::class)
abstract class SyncMicroSchema : RoomDatabase() {
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun mutationLedgerDao(): MutationLedgerDao
    abstract fun nodeContextDao(): NodeContextDao

    companion object {
        const val NAME = "sync_micro_schema.db"
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SyncMicroSchemaConstructor : RoomDatabaseConstructor<SyncMicroSchema> {
    override fun initialize(): SyncMicroSchema
}