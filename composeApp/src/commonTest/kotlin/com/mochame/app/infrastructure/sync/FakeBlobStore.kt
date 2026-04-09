package com.mochame.app.infrastructure.sync

import com.mochame.app.domain.sync.stores.BlobReader
import com.mochame.app.domain.sync.stores.BlobStager
import kotlinx.io.Buffer
import kotlinx.io.Source

/**
 * Just to get build working
 */
class FakeBlobStore: BlobStager, BlobReader {
    override suspend fun stage(source: Source): String {
        return "staged"
    }

    override suspend fun commit(blobId: String) {
    }

    override suspend fun abort(blobId: String) {
    }

    override suspend fun listPendingHashes(): List<String> {
        return emptyList()
    }

    override suspend fun clearIncompleteStaging(): Int {
        return 0
    }

    override fun open(blobId: String): Source {
        return Buffer()
    }

    override fun exists(blobId: String): Boolean {
        return false
    }

}