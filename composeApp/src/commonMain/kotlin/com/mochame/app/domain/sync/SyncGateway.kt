package com.mochame.app.domain.sync

// domain/sync
interface SyncGateway<T> {
    // 1. PULL PHASE
    suspend fun getLatestWatermark(): String?
    suspend fun ingestRemoteChanges(changes: List<T>, newWatermark: String)

    // 2. PUSH PHASE (The State Machine)
    suspend fun getPendingUploads(): List<T>

    /**
     * Moves records from PENDING (1) to SYNCING (2) and tags with sessionId
     */
    suspend fun lockForSync(ids: List<String>, sessionId: String)

    /**
     * Moves records from SYNCING (2) to SYNCED (0) ONLY IF sessionId matches.
     * If a record was edited (moved back to PENDING), this ACK is ignored for that row.
     */
    suspend fun resolveAck(sessionId: String, wasSuccessful: Boolean)
}