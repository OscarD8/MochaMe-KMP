package com.mochame.sync.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.mochame.metadata.GlobalMetadataDao
import com.mochame.core.implementation.GlobalMetadataEntity
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.entities.SyncMetadataEntity

@ConstructedBy(SyncDatabaseConstructor::class)
@Database(
    entities = [SyncMetadataEntity::class, SyncIntentEntity::class, GlobalMetadataEntity::class],
    version = 1
)
abstract class SyncTestDatabase : RoomDatabase() {
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun mutationLedgerDao(): MutationLedgerDao
    abstract fun globalMetaDao(): GlobalMetadataDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SyncDatabaseConstructor : RoomDatabaseConstructor<SyncTestDatabase> {
    override fun initialize(): SyncTestDatabase
}


