package com.mochame.server

import co.touchlab.kermit.Logger
import com.mochame.server.database.ServerDatabase
import com.mochame.server.relay.RelayManager
import com.mochame.server.relay.SessionHandle
import com.mochame.server.utils.FakeWebSocketSession
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


/**
 * Broken, think mix between kotlin test and jupiter?
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RelayServerIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    private val logger = Logger.withTag("ServerTest")
    private lateinit var database: ServerDatabase
    private lateinit var relayManager: RelayManager

    @BeforeEach
    fun setUp() {
        val dbFile = File(tempDir, "test_sync.db").absolutePath
        database = ServerDatabase(dbFile)
        relayManager = RelayManager(logger)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `slow consumer eviction receives violated policy code`() = runTest {
        val fakeSession = FakeWebSocketSession(coroutineContext)
        val handle = SessionHandle("node-x", "group-1", fakeSession)

        relayManager.terminateSession(
            handle,
            CloseReason.Codes.VIOLATED_POLICY,
            "Death by violation"
        )

        // Suspends cleanly until the asynchronous launch { close(...) } actually completes
        val closeReason = fakeSession.awaitCloseReason()
        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, closeReason.code)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun `actor survives unexpected client write crash`() = runTest {
        val fakeSession = FakeWebSocketSession(coroutineContext)
        val handle = SessionHandle("node-x", "group-1", fakeSession)

        // Simulate an OS socket EOF / pipe rupture
        fakeSession.outgoing.close(java.io.IOException("Broken pipe"))

        // Verify RelayManager cleans up without unhandled exceptions crashing the parent job
        relayManager.terminateSession(handle, CloseReason.Codes.INTERNAL_ERROR, "Socket error")
        assertTrue(handle.outboundChannel.isClosedForSend)
    }
}