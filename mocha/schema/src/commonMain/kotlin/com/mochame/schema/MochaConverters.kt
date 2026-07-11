package com.mochame.schema

import androidx.room.TypeConverter
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.resonance.domain.Resonance
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.telemetry.domain.Mood
import com.mochame.sync.api.models.HLC
import kotlin.time.Instant

class MochaConverters {
    /**
     * Converts the Domain Enum to a Database String.
     * Used during @Insert and @Update operations.
     */
    @TypeConverter
    fun fromResonance(resonance: Resonance): String {
        return resonance.name
    }

    /**
     * Converts the Database String back to the Domain Enum.
     * Used during Query/Flow emission to the UI.
     */
    @TypeConverter
    fun toResonance(value: String): Resonance {
        return try {
            Resonance.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Default to a neutral emotion if data is corrupted
            // !!  QUESTION THIS
            Resonance.LOGIC
        }
    }

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
        return millis?.let { Instant.Companion.fromEpochMilliseconds(it) }
    }

    // --- MOOD PAD INTEGRATION ---
    @TypeConverter
    fun fromMood(mood: Mood): String = mood.name

    @TypeConverter
    fun toMood(name: String): Mood = Mood.Companion.fromName(name)


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
    fun fromMochaModule(module: FeatureContext): String = module.featureName

    @TypeConverter
    fun toMochaModule(model: String): FeatureContext = FeatureContext.fromString(model)
}