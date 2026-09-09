package com.mochame.server.database

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredDelta(
    val watermark: Long,
    val payload: ByteArray
)

val dbPath: String = "${System.getProperty("user.home")}/.mochame/sync_server.db"

class ServerDatabase(path: String = dbPath) {
    private val url = "jdbc:sqlite:$dbPath"

    init {
        File(dbPath).parentFile?.mkdirs()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode = WAL;")
                stmt.execute("PRAGMA synchronous = NORMAL;")
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sync_change_log (
                        watermark INTEGER PRIMARY KEY AUTOINCREMENT,
                        group_id TEXT NOT NULL,
                        origin_node_id TEXT NOT NULL,
                        payload BLOB NOT NULL,
                        created_at INTEGER NOT NULL
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_group_watermark 
                    ON sync_change_log(group_id, watermark);
                    """.trimIndent()
                )
            }
        }
    }

    private fun getConnection(): Connection = DriverManager.getConnection(url)

    suspend fun insertDelta(groupId: String, originNodeId: String, payload: ByteArray): Long =
        withContext(Dispatchers.IO) {
            val sql = """
                INSERT INTO sync_change_log (group_id, origin_node_id, payload, created_at)
                VALUES (?, ?, ?, ?);
            """.trimIndent()

            getConnection().use { conn ->
                conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS).use { stmt ->
                    stmt.setString(1, groupId)
                    stmt.setString(2, originNodeId)
                    stmt.setBytes(3, payload)
                    stmt.setLong(4, System.currentTimeMillis())
                    stmt.executeUpdate()

                    val rs = stmt.generatedKeys
                    if (rs.next()) rs.getLong(1) else error("Failed to retrieve generated watermark")
                }
            }
        }

    suspend fun getDeltasSince(
        groupId: String,
        excludeNodeId: String,
        sinceWatermark: Long,
        limit: Int = 500
    ): List<StoredDelta> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT watermark, payload 
            FROM sync_change_log 
            WHERE group_id = ? AND watermark > ? AND origin_node_id != ?
            ORDER BY watermark ASC 
            LIMIT ?;
        """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, groupId)
                stmt.setLong(2, sinceWatermark)
                stmt.setString(3, excludeNodeId)
                stmt.setInt(4, limit)

                val rs = stmt.executeQuery()
                val results = mutableListOf<StoredDelta>()
                while (rs.next()) {
                    results.add(
                        StoredDelta(
                            watermark = rs.getLong("watermark"),
                            payload = rs.getBytes("payload")
                        )
                    )
                }
                results
            }
        }
    }

    suspend fun getMinWatermark(groupId: String): Long? = withContext(Dispatchers.IO) {
        val sql = "SELECT MIN(watermark) FROM sync_change_log WHERE group_id = ?;"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, groupId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val minVal = rs.getLong(1)
                    if (rs.wasNull()) null else minVal
                } else null
            }
        }
    }

    suspend fun pruneExpiredDeltas(olderThanEpochMs: Long): Int = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM sync_change_log WHERE created_at < ?;"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, olderThanEpochMs)
                stmt.executeUpdate()
            }
        }
    }
}