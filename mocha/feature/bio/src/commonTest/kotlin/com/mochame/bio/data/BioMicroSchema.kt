package com.mochame.bio.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.mochame.bio.data.BioDao
import com.mochame.bio.data.DailyContextEntity


@ConstructedBy(BioMicroSchemaConstructor::class)
@Database(
    entities = [DailyContextEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BioMicroSchema : RoomDatabase() {

    abstract fun bioDao(): BioDao

    companion object {
        const val NAME = "bio_micro_schema.db"
    }
}


@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object BioMicroSchemaConstructor : RoomDatabaseConstructor<BioMicroSchema> {
    override fun initialize(): BioMicroSchema
}