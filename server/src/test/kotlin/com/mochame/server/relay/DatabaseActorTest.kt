package com.mochame.server.relay

import com.mochame.server.database.ServerDatabase
import com.mochame.server.utils.ServerConfig
import com.mochame.server.utils.createAndRegisterPeer
import com.mochame.server.utils.createTestIntent
import com.mochame.sync.spi.network.WireFrameFactory
import com.mochame.utils.fixtures.FakeTimeUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.engine.coroutines.testScheduler
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import java.io.File
import java.util.UUID
import kotlin.coroutines.ContinuationInterceptor


class DatabaseActorTest : FunSpec({

    coroutineTestScope = true

    val tempDir = tempdir()
    val dbFile = File(tempDir, "actor_test.db")
    val clock = FakeTimeUtils()
    val relayManager = RelayManager()
    val db = ServerDatabase(path = dbFile.absolutePath, clock = clock, dispatcher = Dispatchers.Unconfined)

    afterSpec {
        db.close()
    }

    fun TestScope.createDatabaseActor(
        config: ServerConfig = ServerConfig.Default,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
    ): DatabaseActor {
        return DatabaseActor(
            database = db,
            relayManager = relayManager,
            config = config,
            scope = backgroundScope,
            dispatcher = dispatcher
        )
    }

    test("should flush solitary intent immediately when channel contains fewer items than batch ceiling") {
        // Given: Solitary write intent and actor configured with maxBroadcastingBatchSize = 10
        val actor = createDatabaseActor(config = ServerConfig(maxBroadcastingBatchSize = 10))
        val groupId = "group-${UUID.randomUUID()}"
        val sender = relayManager.createAndRegisterPeer("node-solitary", groupId)
        val intent = createTestIntent(sender, batchId = 101L, payload = "solitary".encodeToByteArray())

        // When: Submitting solitary intent to writeChannel and processing queue
        actor.writeChannel.send(intent)
        testScheduler.advanceUntilIdle()

        // Then: Immediate flush commits to database and delivers matching ACK frame to sender
        val storedDeltas = db.getDeltasSince(groupId, excludeNodeId = "none", sinceWatermark = 0L)
        storedDeltas.size shouldBe 1

        val frames = sender.drainSentFrames()
        frames.size shouldBe 1
        val ackFrame = frames.first() as Frame.Binary
        ackFrame.data shouldBe WireFrameFactory.ack(batchId = 101L, watermark = storedDeltas.first().watermark)
    }


})