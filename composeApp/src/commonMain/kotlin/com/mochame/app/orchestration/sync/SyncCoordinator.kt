package com.mochame.app.orchestration.sync

//class SyncCoordinator(
//    private val gateways: List<SyncGateway<*>>,
////    private val cloudApi: SyncApi
//) {
//    suspend fun sync() {
//        val sessionId = uuidString() // The sessionUUID
//
//        // 1. PULL PHASE
//        val lastWatermark = gateway.getLocalWatermark()
//        val delta = cloudApi.pull(lastWatermark)
//        gateway.ingestRemoteChanges(delta.records, delta.newWatermark)
//
//        // 2. PUSH PHASE
//        val pending = gateway.getPendingUploads()
//        if (pending.isNotEmpty()) {
//            val ids = pending.map { it.id }
//
//            // Lock the records locally first
//            gateway.lockForSync(ids, sessionId)
//
//            // Upload to the Cloud Vault
//            val response = cloudApi.push(pending, sessionId)
//
//            // 3. ACK PHASE (The Atomic Clear)
//            gateway.resolveAck(sessionId, response.isSuccessful)
//        }
//    }
//}

/*
class SyncCoordinator(
    private val repositories: List<SyncReceiver>, // DI injects all repos here
    private val hlcFactory: HlcFactory
) {
    private val repoMap = repositories.associateBy { it.module }

    suspend fun handleDownstreamPayload(packet: SyncPacket) {
        // 1. Witness the time so the local clock stays ahead of the server
        hlcFactory.witness(HLC.parse(packet.metadata.hlc))

        // 2. Find the right repo "magically"
        val repo = repoMap[packet.module] ?: return

        // 3. Hand off the raw bytes
        repo.processRemoteChange(packet.metadata, packet.payload)
    }
}
 */