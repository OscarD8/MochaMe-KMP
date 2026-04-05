package com.mochame.app.domain.system.sync

import kotlinx.io.Buffer
import kotlinx.io.Source

interface BlobStore {
    /**
     * Optimized Stage: Streams bits directly to /pending.
     * RAM is safe; we only hold a small buffer during the transfer.
     */
    suspend fun stage(source: Source): String

    /**
     * Legacy/Small Stage: For payloads already in memory (<64KB).
     */
    suspend fun stage(payload: ByteArray): String =
        stage(Buffer().apply { write(payload) })

    /**
     * Finalization: Moves file from /pending to /committed.
     */
    suspend fun commit(blobId: String)

    /**
     * Cleanup: Deletes file from disk.
     */
    suspend fun delete(blobId: String)

    /**
     * Janitor Access: Identifies "Dangling Blobs" in staging.
     */
    suspend fun listPending(): List<String>

    suspend fun listCommitted(): List<String>
}