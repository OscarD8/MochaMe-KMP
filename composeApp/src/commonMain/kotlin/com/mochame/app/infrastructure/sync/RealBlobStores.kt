package com.mochame.app.infrastructure.sync

import com.mochame.app.domain.sync.stores.BlobAdmin
import com.mochame.app.domain.sync.stores.BlobReader
import com.mochame.app.domain.sync.stores.BlobStager
import com.mochame.app.infrastructure.utils.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.*
import kotlinx.io.files.*
import kotlin.random.Random

/**
 * LCC-B Compliant BlobStore using kotlinx-io 0.9.0.
 * Confirmed: SystemFileSystem is now the direct entry point.
 */
class RealBlobStore(
    private val dateTimeUtils: DateTimeUtils,
    private val pendingDir: Path,
    private val committedDir: Path
) : BlobStager, BlobReader, BlobAdmin {

    override suspend fun stage(source: Source): String = withContext(Dispatchers.IO) {
        val now = dateTimeUtils.now().toEpochMilliseconds()
        val tempPath = Path(pendingDir, "staging_${now}_${Random.nextLong()}")

        // needs an expect/actual bridge to avoid Java MessageDigest
        val digest = MochaDigest("SHA-256")

        try {
            // SystemFileSystem is the 0.9.0 singleton
            SystemFileSystem.sink(tempPath).buffered().use { sink ->
                val buffer = Buffer()
                while (source.readAtMostTo(buffer, 8192L) != -1L) {
                    val bytes = buffer.peek().readByteArray()
                    digest.update(bytes)
                    sink.write(buffer, buffer.size)
                }
            }

            val blobId = digest.digest().toHexString()
            val finalPendingPath = Path(pendingDir, blobId)

            // Atomic move within the pending chamber
            SystemFileSystem.atomicMove(tempPath, finalPendingPath)
            blobId
        } catch (e: Exception) {
            SystemFileSystem.delete(tempPath)
            throw e
        }
    }

    override suspend fun commit(blobId: String) = withContext(Dispatchers.IO) {
        val pendingPath = Path(pendingDir, blobId)
        val committedPath = Path(committedDir, blobId)

        if (SystemFileSystem.exists(pendingPath)) {
            SystemFileSystem.atomicMove(pendingPath, committedPath)
        }
    }
}