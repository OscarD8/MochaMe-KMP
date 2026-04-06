package com.mochame.app.domain.sync.stores

import kotlinx.io.Source

interface BlobStager {
    /**
     * Streams source to /pending while hashing.
     * Returns the SHA-256 contentBlobId.
     */
    suspend fun stage(source: Source): String

    /**
     * Atomic move from /pending to /committed.
     */
    suspend fun commit(blobId: String)

    /**
     * Cleanup for aborted transactions.
     */
    suspend fun abort(blobId: String)
}

interface BlobReader {
    /** Only allowed to open files from the /committed chamber. */
    fun open(blobId: String): Source

    fun exists(blobId: String): Boolean
}

interface BlobAdmin {
    /** Identify dangling blobs in /pending. */
    suspend fun listPending(): List<String>

    /** Purge orphaned or old files. */
    suspend fun delete(blobId: String)
}