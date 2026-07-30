package com.mochame.support

import androidx.room.TypeConverter
import com.mochame.sync.common.TriState

class SupportConverters {

    @TypeConverter
    fun fromTriState(state: TriState): Int = state.ordinal

    @TypeConverter
    fun toTriState(ordinal: Int) = TriState.fromDb(ordinal)
}