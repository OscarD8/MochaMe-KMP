package com.mochame.app.database.converter

import androidx.room.TypeConverter
import com.mochame.app.domain.model.Emotion

class MochaConverters {
    /**
     * Converts the Domain Enum to a Database String.
     * Used during @Insert and @Update operations.
     */
    @TypeConverter
    fun fromEmotion(emotion: Emotion): String {
        return emotion.name
    }

    /**
     * Converts the Database String back to the Domain Enum.
     * Used during Query/Flow emission to the UI.
     */
    @TypeConverter
    fun toEmotion(value: String): Emotion {
        return try {
            Emotion.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // 2027 Safety: Default to a neutral emotion if data is corrupted
            Emotion.LOGIC
        }
    }
}