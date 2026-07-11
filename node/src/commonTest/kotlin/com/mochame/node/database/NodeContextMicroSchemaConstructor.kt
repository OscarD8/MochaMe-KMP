package com.mochame.node.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.mochame.node.data.NodeContextDao
import com.mochame.node.data.NodeContextEntity


/**
 * Represents the isolated identity of the Node.
 */
@ConstructedBy(NodeContextMicroSchemaConstructor::class)
@Database(
    entities = [NodeContextEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NodeContextMicroSchema : RoomDatabase() {

    abstract fun nodeContextDao(): NodeContextDao

    companion object {
        const val NAME = "node_identity_micro_schema.db"
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object NodeContextMicroSchemaConstructor : RoomDatabaseConstructor<NodeContextMicroSchema> {
    override fun initialize(): NodeContextMicroSchema
}