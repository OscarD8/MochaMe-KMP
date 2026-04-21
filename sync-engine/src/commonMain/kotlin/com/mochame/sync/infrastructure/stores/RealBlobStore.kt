package com.mochame.sync.infrastructure.stores

import co.touchlab.kermit.Logger
import com.mochame.di.IoContext
import com.mochame.sync.domain.providers.Hasher
import com.mochame.sync.domain.providers.digestHex
import com.mochame.sync.domain.stores.BlobReader
import com.mochame.sync.domain.stores.BlobStager
import com.mochame.utils.DateTimeUtils
import com.mochame.utils.exceptions.MochaException
import com.mochame.utils.exceptions.toMochaException
import com.mochame.utils.logger.LogTags
import com.mochame.utils.logger.withTags
import com.mochame.utils.logger.withTimer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlin.coroutines.CoroutineContext
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
    @IoContext private val ioContext: CoroutineContext,
    private val fileSystem: FileSystem,
    private val initMutex: Mutex,
    logger: Logger
) : BlobStager, BlobReader {

    private val logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.SYNC,
        className = "BlobStore"
    )

    private var chambersVerified = false

    // --- STAGING ---

    /**
     * Acting upon abstraction of the platform file system, a unique path is established
     * as a temporary directory. A buffered write process begins which reads the source,
     * generates a fingerprint of its contents at the same time as writing the source to that
     * path. The fingerprint is used to establish a final path for staged sources, before
     * atomically moving the contents to that finalized path.
     * @return blobId representing the fingerprint of the source (its bytes).
     */
    override suspend fun stage(source: Source): String = withContext(ioContext) {
        val mark = TimeSource.Monotonic.markNow()
        val now = dateTimeUtils.now().toEpochMilliseconds()
        val tempPath = Path(pendingDir, "staging_${now}_${Random.Default.nextLong()}")
        val hasher = hashProvider() // expect/actual bridge
        var totalBytes = 0L

        ensureChambersExist()

        try {
            // Read the source, write to sink, through a buffer
            fileSystem.sink(tempPath).buffered().use { sink ->
                val buffer = Buffer()
                while (source.readAtMostTo(buffer, 8192L) != -1L) {
                    val byteCount = buffer.size
                    hasher.update(buffer.peek())
                    sink.write(buffer, byteCount)
                    totalBytes += byteCount
                }
            }

            // Create unique identity for this staging
            val blobId = hasher.digestHex()
            val finalPendingPath = Path(pendingDir, blobId)

            // Deduplication Check
            if (fileSystem.exists(finalPendingPath)) {
                logger.v { "Deduplication: Blob $blobId already staged. Deleting temp." }
                fileSystem.delete(tempPath)
            } else {
                fileSystem.atomicMove(tempPath, finalPendingPath)
            }

            logger.i {
                "Blob Staged | ID: $blobId | Size: ${totalBytes / 1024}KB".withTimer(mark)
            }
            blobId
        } catch (e: Exception) {
            if (fileSystem.exists(tempPath)) fileSystem.delete(tempPath)

            throw e.toMochaException("Blob Staging")
        }
    }

    override suspend fun commit(blobId: String) = withContext(ioContext) {
        val pendingPath = Path(pendingDir, blobId)
        val committedPath = Path(committedDir, blobId)

        try {
            if (fileSystem.exists(pendingPath)) {
                fileSystem.atomicMove(pendingPath, committedPath)
                logger.v { "Blob Committed | ID: $blobId" }
            } else if (!fileSystem.exists(committedPath)) {
                logger.w { "Commit Failed: Blob $blobId not found in pending chamber." }
            }
        } catch (e: Exception) {
            if (!fileSystem.exists(committedPath)) throw e.toMochaException("Blob Commit")
                .also {
                    logger.w(e) { "Possible race condition encountered on a commit? ${e.message}" }
                }
        }

    }

    override suspend fun abort(blobId: String) {
        val abortPath = Path(pendingDir, blobId)

        if (fileSystem.exists(abortPath)) {
            fileSystem.delete(abortPath)
            logger.d { "Blob Aborted | ID: $blobId" }
        }
    }

    // --- ADMIN ---

    /**
     * Returns a list necessary for the reconciliation protocol.
     * These are success stages that failed to atomically transition from
     * pending to committed directories, but are not orphaned. Therefore,
     * a retry attempt is possible.
     */
    override suspend fun listPendingHashes(): List<String> = withContext(ioContext) {
        ensureChambersExist()

        try {
            fileSystem.list(pendingDir)
                .map { it.name }
                .filter { it.length == 64 && !it.startsWith("staging_") } //
        } catch (e: Exception) {
            logger.e(e) { "Infrastructure: Failed to scan pending chamber for hashes." }
            emptyList()
        }
    }

    /**
     * Implementation for clearing aborted/crashed write attempts.
     * Enforces a 1-hour restraint to prevent race conditions with active staging.
     */
    override suspend fun clearIncompleteStaging(): Int = withContext(ioContext) {
        ensureChambersExist()
        var deletedCount = 0
        val now = dateTimeUtils.now().toEpochMilliseconds()
        val oneHourInMillis = 3_600_000L

        try {
            fileSystem.list(pendingDir).forEach { path ->
                val name = path.name
                if (!name.startsWith("staging_")) return@forEach
                // Extraction: "staging_{timestamp}_{random}"
                val parts = name.split("_")
                val fileTimestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val age = now - fileTimestamp

                if (age > oneHourInMillis) {
                    fileSystem.delete(path)
                    deletedCount++
                    logger.v { "Purged stale staging file [${name}] (Age: ${age / 60000} mins)" }
                }
            }

            if (deletedCount > 0) {
                logger.i { "Maintenance Complete: Purged $deletedCount orphaned staging files." }
            }
        } catch (e: Exception) {
            logger.e(e) { "Error during incomplete staging purge: ${e.message}" }
            throw e.toMochaException("Clearing incomplete staging.")
        }

        deletedCount
    }

    // --- READ ACCESS ---

    override fun exists(blobId: String): Boolean {
        val path = Path(committedDir, blobId)
        // Direct check on the singleton fileSystem
        return fileSystem.exists(path)
    }

    override fun open(blobId: String): Source {
        val path = Path(committedDir, blobId)

        if (!fileSystem.exists(path)) {
            throw MochaException.Persistent.FileNotFound(blobId)
        }

        // Returns a RawSource wrapped in a buffered Source.
        return fileSystem.source(path).buffered()
    }

    // --- Helpers ---
    private suspend fun ensureChambersExist() {
        if (chambersVerified) return

        initMutex.withLock {
            if (chambersVerified) return@withLock

            try {
                if (!fileSystem.exists(pendingDir)) {
                    fileSystem.createDirectories(pendingDir)
                    logger.i { "Infrastructure: Initialized Pending Chamber at $pendingDir" }
                }
                if (!fileSystem.exists(committedDir)) {
                    fileSystem.createDirectories(committedDir)
                    logger.i { "Infrastructure: Initialized Committed Chamber at $committedDir" }
                }
                chambersVerified = true
            } catch (e: Exception) {
                throw e.toMochaException("Directory Initialization")
            }
        }
    }
}