package com.mochame.app.domain.repository.sync

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