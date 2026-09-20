package com.mochame.server

import com.mochame.server.database.ServerDatabase
import com.mochame.server.relay.RelayManager
import com.mochame.server.relay.SessionHandle
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.testScheduler
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

class RelayServerIntegrationTest : FunSpec({

    coroutineTestScope = true

    val tempDir = tempdir()

    lateinit var database: ServerDatabase
    lateinit var relayManager: RelayManager
    lateinit var fakeSession: FakeWebSocketSession
    lateinit var testHandle: SessionHandle

    beforeEach {
        val dbFile = File(tempDir, "test_sync.db").absolutePath
        database = ServerDatabase(dbFile)
        relayManager = RelayManager()
        fakeSession = FakeWebSocketSession(coroutineContext)
        testHandle = SessionHandle("node-x", "group-1", fakeSession)
    }

    afterEach {
        database.close()
    }

    test("slow consumer eviction receives violated policy code") {
        relayManager.register(testHandle)

        relayManager.terminate(
            testHandle,
            CloseReason.Codes.VIOLATED_POLICY,
            "Death by violation"
        )

        val closeReason = fakeSession.awaitCloseReason()
        closeReason.code shouldBe CloseReason.Codes.VIOLATED_POLICY.code
        closeReason.message shouldBe "Death by violation"
        relayManager.hasRegisteredSession(testHandle).shouldBeFalse()
    }

//    @OptIn(DelicateCoroutinesApi::class)
//    test("confirm graceful behaviour if the outgoing is closed on a broadcast") {
//        relayManager.register(testHandle)
//
//        fakeSession.outgoing.close(IOException("Broken something"))
//
////        relayManager.broadcast()
//        testScheduler.advanceUntilIdle()
//        testHandle.outboundChannel.isClosedForSend.shouldBeTrue()
//    }
})