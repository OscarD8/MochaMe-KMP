package com.mochame.server.relay

import com.mochame.server.database.ServerDatabase
import com.mochame.server.utils.FakeWebSocketSession
import com.mochame.server.utils.ServerConfig
import com.mochame.server.utils.createWriteIntent
import com.mochame.server.utils.fakeSession
import com.mochame.sync.spi.network.WireFrame
import com.mochame.sync.spi.network.WireFrameFactory
import com.mochame.utils.fixtures.FakeTimeUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.engine.coroutines.testScheduler
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import java.util.UUID
import kotlin.coroutines.ContinuationInterceptor


@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseActorTest : FunSpec({

    coroutineTestScope = true

    val tempDir = tempdir()
    val dbFile = File(tempDir, "actor_test.db")
    val clock = FakeTimeUtils()
    val relayManager = RelayManager()
    val db = ServerDatabase(
        path = dbFile.absolutePath,
        clock = clock,
        dispatcher = Dispatchers.Unconfined // Matches production limited parallelism
    )

    afterSpec {
        db.close()
    }

    fun TestScope.createDatabaseActor(
        config: ServerConfig = ServerConfig.Default,
        dispatcher: CoroutineDispatcher = this.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
    ) = DatabaseActor(
        database = db,
        relayManager = relayManager,
        config = config,
        scope = backgroundScope,
        dispatcher = dispatcher
    )

    suspend fun TestScope.initializePeer(nodeId: String, groupId: String): SessionHandle {
        val session = FakeWebSocketSession(this.coroutineContext)
        val handle = SessionHandle(nodeId, groupId, session)

        handle.completeBackfill(0L)
        relayManager.register(handle)
        return handle
    }


    test("should flush intent immediately when channel contains fewer items than batch ceiling") {
        // Given: Solitary write intent and actor configured with maxBroadcastingBatchSize = 10
        val actor = createDatabaseActor(config = ServerConfig(maxBroadcastingBatchSize = 10))
        val groupId = "group-${UUID.randomUUID()}"
        val sender = initializePeer("node-solitary", groupId)
        val intent = createWriteIntent(sender, batchId = 101L)

        // When:
        actor.writeChannel.send(intent)
        testScheduler.advanceUntilIdle() // outbound worker starts, ships from outbound to outgoing

        // Then: Immediate flush commits to database and delivers matching ACK frame to sender
        val storedDeltas = db.getDeltasSince(groupId, excludeNodeId = "none", sinceWatermark = 0L)
        storedDeltas.size shouldBe 1

        val frames = sender.fakeSession.drainSentFrames()
        val ack = WireFrameFactory.unwrap(frames.first().data).shouldBeInstanceOf<WireFrame.Ack>()
        frames.size shouldBe 1
        ack.batchId shouldBe 101L
        ack.watermark shouldBe storedDeltas.first().watermark

        sender.close()
    }


})