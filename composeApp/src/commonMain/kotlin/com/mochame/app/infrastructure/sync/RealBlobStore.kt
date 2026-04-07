package com.mochame.app.infrastructure.sync

import co.touchlab.kermit.Logger
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.sync.stores.BlobAdmin
import com.mochame.app.domain.sync.stores.BlobReader
import com.mochame.app.domain.sync.stores.BlobStager
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.infrastructure.utils.Hasher
import com.mochame.app.infrastructure.utils.digestHex
import com.mochame.app.infrastructure.utils.toMochaException
import com.mochame.app.infrastructure.utils.withTimer
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * BlobStore using kotlinx-io.
 */
class RealBlobStore(
    private val dateTimeUtils: DateTimeUtils,
    private val pendingDir: Path,
    private val committedDir: Path,
    private val hashProvider: Hasher,
    private val dispatcher: DispatcherProvider,
    logger: Logger
) : BlobStager, BlobReader, BlobAdmin {

    companion object {
        const val TAG = "BlobStore"
    }

    private val logger = logger.appendTag(TAG)

    // --- STAGING ---

    /**
     * Acting upon abstraction of the platform file system, a unique path is established
     * as a temporary directory. A buffered write process begins which reads the source,
     * generates a fingerprint of its contents at the same time as writing the source to that
     * path. The fingerprint is used to establish a final path for staged sources, before
     * atomically moving the contents to that finalized path.
     * @return blobId representing the fingerprint of the source (its bytes).
     */
    override suspend fun stage(source: Source): String = withContext(dispatcher.io) {
        val mark = TimeSource.Monotonic.markNow()
        val now = dateTimeUtils.now().toEpochMilliseconds()
        val tempPath = Path(pendingDir, "staging_${now}_${Random.nextLong()}")
        val hasher = hashProvider() // expect/actual bridge
        var totalBytes = 0L

        ensureChambersExist()

        try {
            // Read the source, write to sink, through a buffer
            SystemFileSystem.sink(tempPath).buffered().use { sink ->
                val buffer = Buffer()
                while (source.readAtMostTo(buffer, 8192L) != -1L) {
                    val byteCount = buffer.size
                    val bytes = buffer.peek().readByteArray()
                    hasher.update(bytes)
                    sink.write(buffer, byteCount)
                    totalBytes += byteCount
                }
            }

            // Create unique identity for this staging
            val blobId = hasher.digestHex()
            val finalPendingPath = Path(pendingDir, blobId)

            // Deduplication Check
            if (SystemFileSystem.exists(finalPendingPath)) {
                logger.v { "Deduplication: Blob $blobId already staged. Deleting temp." }
                SystemFileSystem.delete(tempPath)
            } else {
                SystemFileSystem.atomicMove(tempPath, finalPendingPath)
            }

            logger.i {
                "Blob Staged | ID: $blobId | Size: ${totalBytes / 1024}KB".withTimer(mark)
            }
            blobId
        } catch (e: Exception) {
            if (SystemFileSystem.exists(tempPath)) SystemFileSystem.delete(tempPath)

            throw e.toMochaException("Blob Staging")
        }
    }

    override suspend fun commit(blobId: String) = withContext(dispatcher.io) {
        val pendingPath = Path(pendingDir, blobId)
        val committedPath = Path(committedDir, blobId)

        if (SystemFileSystem.exists(pendingPath)) {
            SystemFileSystem.atomicMove(pendingPath, committedPath)
            logger.v { "Blob Committed | ID: $blobId" }
        } else if (!SystemFileSystem.exists(committedPath)) {
            logger.w { "Commit Failed: Blob $blobId not found in pending chamber." }
        }
    }

    override suspend fun abort(blobId: String) {
        val abortPath = Path(pendingDir, blobId)

        if (SystemFileSystem.exists(abortPath)) {
            SystemFileSystem.delete(abortPath)
            logger.d { "Blob Aborted | ID: $blobId" }
        }
    }

    // --- ADMIN ---

    override suspend fun listPending(): List<String> = withContext(dispatcher.io) {
        ensureChambersExist()

        try {
            // SystemFileSystem.list returns a list of Paths in the directory.
            SystemFileSystem.list(pendingDir).map { it.name }.also { items ->
                val orphans = items.count { it.startsWith("staging_") || it.length == 64 }
                if (orphans > 0) {
                    logger.w { "Detected $orphans orphaned staging files in pendingDir." }
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to audit pending chamber." }
            throw e.toMochaException("Pending Chamber Audit")
        }
    }

    override suspend fun delete(blobId: String) = withContext(dispatcher.io) {
        val pendingPath = Path(pendingDir, blobId)
        val committedPath = Path(committedDir, blobId)

        // Write-authority to both chambers.
        try {
            if (SystemFileSystem.exists(pendingPath)) {
                SystemFileSystem.delete(pendingPath)
            }
            if (SystemFileSystem.exists(committedPath)) {
                SystemFileSystem.delete(committedPath)
            }
        } catch (e: Exception) {
            logger.w { "Could not purge blob $blobId: ${e.message}" }
        }
    }

    // --- READ ACCESS ---

    override fun exists(blobId: String): Boolean {
        val path = Path(committedDir, blobId)
        // Direct check on the singleton SystemFileSystem
        return SystemFileSystem.exists(path)
    }

    override fun open(blobId: String): Source {
        val path = Path(committedDir, blobId)

        if (!SystemFileSystem.exists(path)) {
            throw MochaException.Persistent.FileNotFound(blobId)
        }

        // Returns a RawSource wrapped in a buffered Source.
        return SystemFileSystem.source(path).buffered()
    }

    // --- Helpers ---
    private fun ensureChambersExist() {
        try {
            if (!SystemFileSystem.exists(pendingDir)) {
                SystemFileSystem.createDirectories(pendingDir)
                logger.i { "Infrastructure: Initialized Pending Chamber at $pendingDir" }
            }
            if (!SystemFileSystem.exists(committedDir)) {
                SystemFileSystem.createDirectories(committedDir)
                logger.i { "Infrastructure: Initialized Committed Chamber at $committedDir" }
            }
        } catch (e: Exception) {
            throw e.toMochaException("Directory Initialization")
        }
    }
}