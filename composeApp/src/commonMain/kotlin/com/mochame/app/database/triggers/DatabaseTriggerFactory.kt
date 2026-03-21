package com.mochame.app.database.triggers

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mochame.app.core.MochaModule
import com.mochame.app.core.SyncStatus
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.entity.SyncMetadataEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// commonMain
object DatabaseTriggerFactory {
    fun createAllTriggers(): List<String> = listOf(
        """
        CREATE TRIGGER IF NOT EXISTS tr_domain_tombstone
        AFTER DELETE ON domains
        BEGIN
            INSERT INTO sync_tombstones (entityId, tableName, deletedAt)
            VALUES (OLD.id, 'domains', (STRFTIME('%s','now') * 1000));
        END;
        """,
        """
        CREATE TRIGGER IF NOT EXISTS tr_topic_tombstone
        AFTER DELETE ON topics
        BEGIN
            INSERT INTO sync_tombstones (entityId, tableName, deletedAt)
            VALUES (OLD.id, 'topics', (STRFTIME('%s','now') * 1000));
        END;
        """,
        """
        CREATE TRIGGER IF NOT EXISTS tr_daily_context_tombstone
        AFTER DELETE ON daily_context
        BEGIN
            INSERT INTO sync_tombstones (entityId, tableName, deletedAt)
            VALUES (OLD.id, 'daily_context', (strftime('%s','now') * 1000));
        END;
        """,
        """
        CREATE TRIGGER IF NOT EXISTS tr_moment_tombstone
        AFTER DELETE ON moments
        BEGIN
            INSERT INTO sync_tombstones (entityId, tableName, deletedAt)
            VALUES (OLD.id, 'moments', (STRFTIME('%s','now') * 1000));
        END;
        """
    )
}

val SYNC_TRIGGER_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        DatabaseTriggerFactory.createAllTriggers().forEach { sql ->
            connection.prepare(sql).use { it.step() }
        }
    }

    override fun onOpen(connection: SQLiteConnection) {
        DatabaseTriggerFactory.createAllTriggers().forEach { sql ->
            connection.prepare(sql).use { it.step() }
        }
    }
}