package com.mochame.sync.test.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.mochame.sync.data.SyncConverters
import com.mochame.sync.data.daos.SyncIntentDao
import com.mochame.sync.data.daos.SyncModuleStateDao
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.entities.SyncModuleStateEntity


@ConstructedBy(SyncMicroSchemaConstructor::class)
@Database(
    entities = [SyncModuleStateEntity::class, SyncIntentEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SyncConverters::class)
abstract class SyncMicroSchema : RoomDatabase() {
    abstract fun syncMetadataDao(): SyncModuleStateDao
    abstract fun mutationLedgerDao(): SyncIntentDao

    companion object {
        const val NAME = "sync_micro_schema.db"
    }
}

expect object SyncMicroSchemaConstructor : RoomDatabaseConstructor<SyncMicroSchema> {
    override fun initialize(): SyncMicroSchema
}