package com.mochame.sync.fixtures

import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.domain.model.QuarantinedFeatureSummary
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.models.SyncIntent
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSyncIntentStore : SyncIntentStore, SyncIntentMaintenanceStore {

    private val lock = reentrantLock()

    // TODO
    private val _intents = linkedMapOf<HLC, SyncIntent>()
    private val _failingBlobIds = mutableSetOf<String>()

    var failWith: Exception?
        set(value) = lock.withLock { _failWith = value }
        get() = lock.withLock { _failWith }

    private var _failWith: Exception? = null

    private val _quarantinedFlow =
        MutableStateFlow<List<QuarantinedFeatureSummary>>(emptyList())

    val intents: List<SyncIntent>
        get() = lock.withLock { _intents.values.toList() }


    fun seedIntents(vararg entries: SyncIntent) {
        seedIntents(entries.asList())
    }

    fun seedIntents(entries: Collection<SyncIntent>) {
        val summaries = lock.withLock {
            entries.forEach { _intents[it.hlc] = it }
            calculateQuarantinedSummariesLocked()
        }
        _quarantinedFlow.value = summaries
    }

    fun failOnBlobCheck(blobId: String) = lock.withLock {
        _failingBlobIds.add(blobId)
    }

    fun reset() {
        lock.withLock { _intents.clear() }
        _quarantinedFlow.value = emptyList()
    }

    override suspend fun getPendingByCandidateKey(candidateKey: String): SyncIntent? =
        lock.withLock {
            _intents.values.find { it.candidateKey == candidateKey && it.syncStatus == SyncStatus.PENDING }
        }

    override suspend fun getPendingByFeature(feature: FeatureContext): List<SyncIntent?> =
        lock.withLock {
            _intents.values.filter {
                it.featureContext.featureName == feature.featureName && it.syncStatus == SyncStatus.PENDING
            }
        }

    override suspend fun recordIntent(entry: SyncIntent) {
        val updatedSummaries = lock.withLock {
            _intents[entry.hlc] = entry
            calculateQuarantinedSummariesLocked()
        }
        _quarantinedFlow.value = updatedSummaries
    }

    override suspend fun discardIntent(hlc: HLC) {
        val updatedSummaries = lock.withLock {
            _intents.remove(hlc)
            calculateQuarantinedSummariesLocked()
        }
        _quarantinedFlow.value = updatedSummaries
    }

    override suspend fun stampLastError(hlcs: List<HLC>, message: String) {
        lock.withLock {
            val hlcSet = hlcs.toSet()
            _intents.values.filter { it.hlc in hlcSet }.forEach { intent ->
                _intents[intent.hlc] = intent.copy(lastErrorMessage = message)
            }
        }
    }

    override suspend fun claimBatch(batchId: String, limit: Int): Int = lock.withLock {
        if (limit <= 0) return 0
        var claimedCount = 0

        val eligible = _intents.values
            .filter { it.syncId == null && it.syncStatus == SyncStatus.PENDING }
            .sortedBy { it.hlc.toString() }

        for (intent in eligible) {
            if (claimedCount == limit) break
            _intents[intent.hlc] =
                intent.copy(syncStatus = SyncStatus.SYNCING, syncId = batchId)
            claimedCount++
        }
        return claimedCount
    }

    override suspend fun getClaimedBatch(batchId: String): List<SyncIntent> =
        lock.withLock {
            _intents.values
                .filter { it.syncId == batchId }
                .sortedBy { it.hlc.toString() }
        }

    override suspend fun acknowledgeSuccess(hlcList: List<HLC>) {
        lock.withLock {
            for (hlc in hlcList) {
                _intents[hlc]?.let { intent ->
                    _intents[hlc] =
                        intent.copy(syncStatus = SyncStatus.SUCCESS, syncId = null)
                }
            }
        }
    }

    override suspend fun clearAllLocksAndResetToPending(): Int = lock.withLock {
        _failWith?.let { throw it }

        var alteredCount = 0
        _intents.values.filter { it.syncId != null }.forEach { intent ->
            _intents[intent.hlc] =
                intent.copy(syncId = null, syncStatus = SyncStatus.PENDING)
            alteredCount++
        }
        return alteredCount
    }

    override suspend fun resetLease(hlc: HLC, retryCount: Int) {
        lock.withLock {
            _intents[hlc]?.let { intent ->
                _intents[hlc] = intent.copy(
                    retryCount = retryCount,
                    syncStatus = SyncStatus.PENDING,
                    syncId = null,
                    leasedAt = null
                )
            }
        }
    }

    override suspend fun pruneOldSynced(pruneAfter: Long, limit: Int): Int =
        lock.withLock {
            _failWith?.let { throw it }

            var pruned = 0
            val keysToRemove = mutableListOf<HLC>()

            val candidates = _intents.values
                .filter { it.syncStatus == SyncStatus.SUCCESS && it.createdAt < pruneAfter }
                .sortedBy { it.hlc.toString() }

            for (intent in candidates) {
                if (pruned >= limit) break
                keysToRemove.add(intent.hlc)
                pruned++
            }

            keysToRemove.forEach { _intents.remove(it) }
            pruned
        }

    override suspend fun quarantine(hlc: HLC, retryCount: Int) {
        val updatedSummaries = lock.withLock {
            _intents[hlc]?.let { intent ->
                _intents[hlc] = intent.copy(
                    syncStatus = SyncStatus.QUARANTINED,
                    retryCount = retryCount
                )
            }
            calculateQuarantinedSummariesLocked()
        }
        _quarantinedFlow.value = updatedSummaries
    }

    override suspend fun observeQuarantinedCountByModule(): Flow<List<QuarantinedFeatureSummary>> {
        return _quarantinedFlow.asStateFlow()
    }

    override suspend fun getStaleLeasedIntents(olderThan: Long): List<SyncIntent> =
        lock.withLock {
            _intents.values.filter { intent ->
                intent.syncId != null && intent.syncStatus == SyncStatus.SYNCING &&
                        intent.leasedAt != null && intent.leasedAt!! < olderThan
            }
        }

    override suspend fun existsForBlob(blobId: String): Boolean {
        if (blobId in _failingBlobIds) {
            throw IllegalStateException("Simulated database read failure for blob $blobId")
        }
        return intents.any { it.overflowBlobId == blobId }
    }

    private fun calculateQuarantinedSummariesLocked(): List<QuarantinedFeatureSummary> {
        return _intents.values
            .filter { it.syncStatus == SyncStatus.QUARANTINED }
            .groupBy { it.featureContext }
            .map { (feature, items) ->
                QuarantinedFeatureSummary(feature = feature, count = items.size)
            }
    }
}