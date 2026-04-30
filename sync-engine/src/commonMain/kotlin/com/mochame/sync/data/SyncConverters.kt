package com.mochame.sync.data

import androidx.room.TypeConverter
import com.mochame.contract.metadata.MochaModule
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.domain.state.SyncStatus
import com.mochame.sync.infrastructure.HLC
import kotlin.time.Instant


class SyncConverters {

    /**
     * Bridges the gap between the Domain's [Instant] and the Database's [Long].
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