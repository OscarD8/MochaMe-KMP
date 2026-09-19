package com.mochame.server.relay

import co.touchlab.kermit.Logger
import com.mochame.server.config.ServerLogger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class RelayManager(
    private val logger: Logger = ServerLogger.base.withTag("RelayMgr")
) {

    private val groupSessions =
        ConcurrentHashMap<String, ConcurrentHashMap<String, SessionHandle>>()

    fun register(handle: SessionHandle): SessionHandle? {
        var previous: SessionHandle? = null
        groupSessions.compute(handle.groupId) { _, existingGroup ->
            val group = existingGroup ?: ConcurrentHashMap()
            previous = group.put(handle.nodeId, handle)
            group
        }
        logger.i { "Registered node '${handle.nodeId}' to group '${handle.groupId}'" }
        return previous
    }

    fun unregister(groupId: String, nodeId: String, handle: SessionHandle? = null): SessionHandle? {
        var removed: SessionHandle? = null
        groupSessions.compute(groupId) { _, group ->
            if (group == null) return@compute null
            val current = group[nodeId]
            if (handle == null || current === handle) {
                removed = group.remove(nodeId)
                logger.i { "Unregistered node '$nodeId' from group '$groupId'" }
            }
            if (group.isEmpty()) null else group
        }
        removed?.outboundChannel?.close()
        return removed
    }

    fun broadcast(groupId: String, excludeNodeId: String, watermark: Long, frame: Frame) {
        val peers = groupSessions[groupId]?.values ?: return

        for (peer in peers) {
            if (peer.nodeId == excludeNodeId) continue

            val enqueued = peer.enqueueBroadcast(
                watermark,
                frame
            ) // if this fails because buffer is full, and we terminate the session, can a heavy backfill ever actually complete?
            if (!enqueued) {
                terminateSession(
                    peer = peer,
                    code = CloseReason.Codes.VIOLATED_POLICY,
                    reason = "SLOW_CONSUMER: Outbound egress buffer saturated"
                )
            }
        }
    }

    fun terminateSession(
        peer: SessionHandle,
        code: CloseReason.Codes,
        reason: String
    ) {
        logger.w { "Terminating node '${peer.nodeId}' [${code.name}]: $reason" }
        unregister(peer.groupId, peer.nodeId, peer)

        peer.session.launch {
            try {
                peer.session.close(CloseReason(code, reason))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.v { "Socket already closed for node '${peer.nodeId}': ${e.message}" }
            }
        }
    }
}