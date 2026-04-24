package com.mochame.sync.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.mochame.metadata.MochaModule
import com.mochame.metadata.MutationOp
import com.mochame.platform.global.GlobalMetadataDao
import com.mochame.platform.global.GlobalMetadataEntity
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.entities.SyncMetadataEntity
import com.mochame.sync.domain.SyncStatus
import com.mochame.sync.infrastructure.HLC
import kotlin.time.Instant

@ConstructedBy(SyncDatabaseConstructor::class)
@Database(
    entities = [SyncMetadataEntity::class, SyncIntentEntity::class, GlobalMetadataEntity::class],
    version = 1
)
@TypeConverters(SyncConverters::class)
abstract class SyncTestDatabase : RoomDatabase() {
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun mutationLedgerDao(): MutationLedgerDao
    abstract fun globalMetaDao(): GlobalMetadataDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SyncDatabaseConstructor : RoomDatabaseConstructor<SyncTestDatabase> {
    override fun initialize(): SyncTestDatabase
}


class SyncConverters {

    // --- TEMPORAL INSTANTS ---
    /**
     * Bridges the gap between the Domain's [kotlin.time.Instant] and the Database's [Long].
     * Essential for sync and timestamps.
     */
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilliseconds()
    }

    @TypeConverter
    fun toInstant(millis: Long?): Instant? {
        return millis?.let { Instant.fromEpochMilliseconds(it) }
    }

    // --- SYNC TOOLS ---
    @TypeConverter
    fun fromHlc(hlc: HLC): String = hlc.toString()

    @TypeConverter
    fun toHlc(hlcString: String): HLC = HLC.parse(hlcString)

    // MutationOp <-> Int
    @TypeConverter
    fun fromOp(op: MutationOp): Int = op.id

    @TypeConverter
    fun toOp(id: Int): MutationOp = MutationOp.fromId(id)

    // SyncStatus <-> Int
    @TypeConverter
    fun fromStatus(status: SyncStatus): Int = status.id

    @TypeConverter
    fun toStatus(id: Int): SyncStatus = SyncStatus.fromId(id)

    @TypeConverter
    fun fromMochaModule(module: MochaModule): String = module.tag

    @TypeConverter
    fun toMochaModule(tag: String): MochaModule {
        return MochaModule.entries.find { it.tag == tag }
            ?: throw IllegalArgumentException("Unknown MochaModule tag: $tag")
    }
}