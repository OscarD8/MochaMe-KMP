package com.mochame.app.infrastructure.sync

import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.sync.stores.BlobAdmin
import com.mochame.app.domain.sync.stores.BlobReader
import com.mochame.app.domain.sync.stores.BlobStager
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.infrastructure.utils.Digest
import com.mochame.app.infrastructure.utils.Hasher
import com.mochame.app.infrastructure.utils.digestHex
import com.mochame.app.infrastructure.utils.sha256Hasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.random.Random

/**
 * LCC-B Compliant BlobStore using kotlinx-io 0.9.0.
 * Confirmed: SystemFileSystem is now the direct entry point.
 */
class RealBlobStore(
    private val dateTimeUtils: DateTimeUtils,
    private val pendingDir: Path,
    private val committedDir: Path,
    private val hashProvider: Hasher,
    private val dispatcherProvider: DispatcherProvider
) : BlobStager, BlobReader, BlobAdmin {

    override suspend fun stage(source: Source): String = withContext(dispatcherProvider.io) {
        val now = dateTimeUtils.now().toEpochMilliseconds()
        val tempPath = Path(pendingDir, "staging_${now}_${Random.nextLong()}")

        // needs an expect/actual bridge to avoid Java MessageDigest in commonMain
        val hasher = hashProvider()

        try {
            // SystemFileSystem is the 0.9.0 singleton
            SystemFileSystem.sink(tempPath).buffered().use { sink ->
                val buffer = Buffer()
                while (source.readAtMostTo(buffer, 8192L) != -1L) {
                    val bytes = buffer.peek().readByteArray()
                    hasher.update(bytes)
                    sink.write(buffer, buffer.size)
                }
            }

            val blobId = hasher.digestHex()
            val finalPendingPath = Path(pendingDir, blobId)

            // Atomic move within the pending chamber
            SystemFileSystem.atomicMove(tempPath, finalPendingPath)
            blobId
        } catch (e: Exception) {
            SystemFileSystem.delete(tempPath)
            throw e
        }
    }

    override suspend fun commit(blobId: String) = withContext(dispatcherProvider.io) {
        val pendingPath = Path(pendingDir, blobId)
        val committedPath = Path(committedDir, blobId)

        if (SystemFileSystem.exists(pendingPath)) {
            SystemFileSystem.atomicMove(pendingPath, committedPath)
        }
    }
}