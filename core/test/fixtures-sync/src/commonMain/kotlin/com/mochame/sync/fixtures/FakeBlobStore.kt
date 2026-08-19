package com.mochame.sync.fixtures

import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.spi.infrastructure.BlobStore
import com.mochame.sync.spi.infrastructure.DigestFactory
import com.mochame.sync.spi.infrastructure.digestHex
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray

class FakeBlobStore(
    private val digestFactory: DigestFactory
) : BlobStore {

    private val lock = reentrantLock()

    private val _pendingBlobs = mutableMapOf<String, ByteArray>()
    private val _committedBlobs = mutableMapOf<String, ByteArray>()
    private var _stageCallCount = 0
    private var _commitCallCount = 0
    private var _abortCallCount = 0
    private var pendingError: Throwable? = null

    // --- Telemetry Properties ---

    val stageCallCount: Int get() = lock.withLock { _stageCallCount }
    val commitCallCount: Int get() = lock.withLock { _commitCallCount }
    val abortCallCount: Int get() = lock.withLock { _abortCallCount }
    val pendingCount: Int get() = lock.withLock { _pendingBlobs.size }
    val committedCount: Int get() = lock.withLock { _committedBlobs.size }

    // --- BlobStore Operations ---

    override suspend fun stage(source: Source): String {
        val digest = digestFactory()
        val buffer = Buffer()

        // Stream bytes into buffer while feeding DigestState
        while (source.readAtMostTo(buffer, 8192L) != -1L) {
            digest.update(buffer)
        }
        val bytes = buffer.readByteArray()
        val blobId = digest.digestHex()

        lock.withLock {
            checkAndThrowPendingError()
            _stageCallCount++
            _pendingBlobs[blobId] = bytes
        }
        return blobId
    }

    override suspend fun commit(blobId: String) {
        lock.withLock {
            checkAndThrowPendingError()
            _commitCallCount++
            val payload = _pendingBlobs.remove(blobId)
            if (payload != null) {
                _committedBlobs[blobId] = payload
            }
        }
    }

    override suspend fun abort(blobId: String) {
        lock.withLock {
            checkAndThrowPendingError()
            _abortCallCount++
            _pendingBlobs.remove(blobId)
        }
    }

    override suspend fun listPendingHashes(): List<String> = lock.withLock {
        _pendingBlobs.keys.toList()
    }

    override suspend fun clearIncompleteStaging(): Int = lock.withLock {
        val count = _pendingBlobs.size
        _pendingBlobs.clear()
        count
    }

    override suspend fun existsInCommitted(blobId: String): Boolean = lock.withLock {
        _committedBlobs.containsKey(blobId)
    }

    override suspend fun existsInPending(blobId: String): Boolean = lock.withLock {
        _pendingBlobs.containsKey(blobId)
    }

    override suspend fun open(blobId: String): Source {
        val bytes = lock.withLock {
            checkAndThrowPendingError()
            _committedBlobs[blobId]
        } ?: throw MochaException.Transient.FileNotFound(blobId)

        return Buffer().apply { write(bytes) }
    }

    // --- Test Helpers ---

    fun putCommittedBlob(blobId: String, content: ByteArray) = lock.withLock {
        _committedBlobs[blobId] = content
    }

    fun getCommittedBytes(blobId: String): ByteArray? = lock.withLock {
        _committedBlobs[blobId]?.copyOf()
    }

    fun throwOnNextOperation(throwable: Throwable) = lock.withLock {
        pendingError = throwable
    }

    fun reset() = lock.withLock {
        _pendingBlobs.clear()
        _committedBlobs.clear()
        _stageCallCount = 0
        _commitCallCount = 0
        _abortCallCount = 0
        pendingError = null
    }

    private fun checkAndThrowPendingError() {
        val error = pendingError
        pendingError = null
        error?.let { throw it }
    }
}