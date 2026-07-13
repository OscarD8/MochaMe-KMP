package com.mochame.sync.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters


@ConstructedBy(SyncMicroSchemaConstructor::class)
@Database(
    entities = [SyncIntentEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SyncConverters::class)
internal abstract class SyncMicroSchema : RoomDatabase() {
    internal abstract fun syncIntentDao(): SyncIntentDao

    internal companion object {
        const val NAME = "sync_micro_schema.db"
    }
}

internal expect object SyncMicroSchemaConstructor : RoomDatabaseConstructor<SyncMicroSchema> {
    override fun initialize(): SyncMicroSchema
}