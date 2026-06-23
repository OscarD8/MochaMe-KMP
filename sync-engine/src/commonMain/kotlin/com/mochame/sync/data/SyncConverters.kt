package com.mochame.sync.data

import androidx.room.TypeConverter
import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.SyncStatus
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
    fun fromContextType(type: MochaModuleContext.Type?): String? {
        return type?.name
    }

    @TypeConverter
    fun toContextType(databaseValue: String?): MochaModuleContext.Type {
        if (databaseValue == null) return MochaModuleContext.Type.UNRECOGNIZED_FALLBACK

        return try {
            MochaModuleContext.Type.valueOf(databaseValue)
        } catch (e: IllegalArgumentException) {
            MochaModuleContext.Type.UNRECOGNIZED_FALLBACK
        }
    }
}