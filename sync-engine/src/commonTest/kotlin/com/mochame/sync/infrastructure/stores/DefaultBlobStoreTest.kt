@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.infrastructure.stores

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.di.blob.BlobStoreTestApp
import com.mochame.sync.di.blob.BlobStoreTestEnv
import com.mochame.utils.fixtures.TestPayloads
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------

private inline fun runEnv(crossinline block: suspend BlobStoreTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<BlobStoreTestEnv>(
        koinSetup = { includes(koinConfiguration<BlobStoreTestApp>()) },
        block = block
    )


internal class DefaultBlobStoreTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // STAGING AND DEDUPLICATION
    // -----------------------------------------------------------

    @Test
    fun should_stagePayloadToPending_when_stagingNewSource() = runEnv {
        // Arrange
        val source = TestPayloads.defaultSource()

        // Act
        val blobId = store.stage(source)

        // Assert
        assertTrue(
            store.existsInPending(blobId),
            "Expected blob $blobId to exist in pending directory"
        )
        assertEquals(
            1,
            digestFactory.invokeCount,
            "Digest factory should be invoked exactly once per stage operation"
        )
        assertEquals(
            listOf(blobId),
            store.listPendingHashes(),
            "Pending directory should contain exactly the newly staged blob ID"
        )
    }

    @Test
    fun should_deduplicateAndRetainOriginal_when_stagingIdenticalPayload() = runEnv {
        // Arrange
        val firstSource = TestPayloads.defaultSource()
        val secondSource = TestPayloads.defaultSource()

        // Act
        val initialBlobId = store.stage(firstSource)
        val duplicateBlobId = store.stage(secondSource)

        // Assert
        assertEquals(
            initialBlobId,
            duplicateBlobId,
            "Staging identical payloads must produce matching blob IDs"
        )
        assertTrue(
            store.existsInPending(initialBlobId),
            "Original blob $initialBlobId must remain intact in pending directory"
        )
        assertEquals(
            2,
            digestFactory.invokeCount,
            "Digest factory should be invoked once per staging attempt"
        )
        assertEquals(
            listOf(initialBlobId),
            store.listPendingHashes(),
            "Deduplication must clean up temporary staging files without leaving orphaned entries"
        )
    }

    @Test
    fun should_cleanUpTempStagingFileAndRethrowMochaException_when_hasherFailsDuringUpdate() = runEnv {
        // Arrange
        val source = TestPayloads.defaultSource()
        val expectedCause = IllegalStateException("Simulated native OpenSSL allocation failure")
        digestFactory.shouldThrow = expectedCause

        // Act & Assert Exception Wrapping
        val thrownException = assertFailsWith<MochaException> {
            store.stage(source)
        }

        // Assert
        assertEquals(
            thrownException.message.contains("Blob Staging"),
            true,
            "Exception must be converted to MochaException with staging context"
        )
        assertEquals(
            emptyList(),
            store.listPendingHashes(),
            "Pending directory must remain completely clean after a failed staging attempt"
        )
    }

    // -----------------------------------------------------------
    // DOMAIN B: LIFECYCLE STATE TRANSITIONS (COMMIT & ABORT)
    // -----------------------------------------------------------

    @Test
    fun should_moveBlobToCommitted_when_committingPendingBlob() = runEnv {
        // Arrange
        val source = TestPayloads.defaultSource()
        val blobId = store.stage(source)

        assertTrue(
            store.existsInPending(blobId),
            "Prerequisite: Blob must exist in pending before commit"
        )
        assertFalse(
            store.existsInCommitted(blobId),
            "Prerequisite: Blob must not exist in committed before commit"
        )

        // Act
        store.commit(blobId)

        // Assert
        assertFalse(
            store.existsInPending(blobId),
            "Blob should no longer exist in pending after successful commit"
        )
        assertTrue(
            store.existsInCommitted(blobId),
            "Blob must exist in committed directory after atomic move"
        )
    }

    @Test
    fun should_logWarningAndNotThrow_when_committingNonExistentBlob() = runEnv {
        // Arrange
        val nonExistentBlobId = "0000000000000000000000000000000000000000000000000000000000000000"

        // Act & Assert - Should gracefully log warning without throwing an exception
        store.commit(nonExistentBlobId)

        assertFalse(
            store.existsInCommitted(nonExistentBlobId),
            "Non-existent blob should not magically appear in committed directory"
        )
        assertFalse(
            store.existsInPending(nonExistentBlobId),
            "Non-existent blob should not exist in pending directory"
        )
        with(writer.logs[0]) {
            assertEquals(Severity.Warn, severity)
        }
    }

    @Test
    fun should_removeBlobFromPending_when_abortingPendingBlob() = runEnv {
        // Arrange
        val source = TestPayloads.defaultSource()
        val blobId = store.stage(source)

        assertTrue(
            store.existsInPending(blobId),
            "Prerequisite: Blob must exist in pending prior to abort"
        )

        // Act
        store.abort(blobId)

        // Assert
        assertFalse(
            store.existsInPending(blobId),
            "Aborted blob must be completely deleted from pending directory"
        )
        assertFalse(
            store.existsInCommitted(blobId),
            "Aborted blob must never reach committed directory"
        )
    }
}