package com.mochame.server.utils

import com.mochame.server.relay.DeltaWriteIntent
import com.mochame.server.relay.RelayManager
import com.mochame.server.relay.SessionHandle
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class TestPeer(
    val handle: SessionHandle,
    val session: FakeWebSocketSession
) {
    val nodeId: String get() = handle.nodeId
    val groupId: String get() = handle.groupId

    fun drainSentFrames(): List<Frame> = session.drainSentFrames()
    suspend fun awaitCloseReason(): CloseReason = session.awaitCloseReason()
}

suspend fun RelayManager.createAndRegisterPeer(
    nodeId: String,
    groupId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
): TestPeer {
    val session = FakeWebSocketSession(dispatcher)
    val handle = SessionHandle(nodeId, groupId, session)
    handle.completeBackfill(0L)
    register(handle)
    return TestPeer(handle, session)
}

fun createTestIntent(
    peer: TestPeer,
    batchId: Long = 1L,
    payload: ByteArray = "payload".encodeToByteArray()
): DeltaWriteIntent = DeltaWriteIntent(
    groupId = peer.groupId,
    originNodeId = peer.nodeId,
    batchId = batchId,
    rawPayload = payload,
    senderHandle = peer.handle
)

fun createTestIntent(
    groupId: String,
    nodeId: String,
    payload: ByteArray = byteArrayOf(),
    batchId: Long = 1L
): DeltaWriteIntent {
    val handle = SessionHandle(nodeId, groupId, FakeWebSocketSession())
    return DeltaWriteIntent(groupId, nodeId, batchId, payload, handle)
}