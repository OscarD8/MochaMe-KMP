package com.mochame.app.assembly

import androidx.room.TypeConverter
import com.mochame.resonance.domain.Resonance
import com.mochame.telemetry.domain.Mood

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

    // --- MOOD PAD INTEGRATION ---
    @TypeConverter
    fun fromMood(mood: Mood): String = mood.name

    @TypeConverter
    fun toMood(name: String): Mood = Mood.fromName(name)
}