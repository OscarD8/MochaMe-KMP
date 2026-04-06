package com.mochame.app.data.local.room.sync

import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
import com.mochame.app.data.local.room.entities.SyncMetadataEntity
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.domain.sync.stores.MetadataStore
import com.mochame.app.domain.sync.stores.MetadataStoreMaintenance
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.infrastructure.sync.HLC
import kotlin.time.Clock

class RoomMetadataStore(
    private val dao: SyncMetadataDao
) : MetadataStore, MetadataStoreMaintenance {

    override suspend fun recordPendingMetadata(
        module: MochaModule,
        hlc: HLC
    )  {
        dao.recordLocalMutation(
            module = module,
            hlc = hlc.toString(),
            now = hlc.ts,
            syncStatus = SyncStatus.PENDING
        )
    }

    override suspend fun stampModuleMetadata(
        module: MochaModule,
        watermark: String?,
        timestamp: Long,
        status: SyncStatus
    )  {
        dao.stampMetadata(
            module,
            watermark,
            timestamp,
            status
        )
    }

    // --- Routing Transitions ---
    override suspend fun updatePendingToSyncing(
        module: MochaModule,
        fromStatus: SyncStatus,
        toStatus: SyncStatus
    ) = internalTransition(module, fromStatus, toStatus)

    override suspend fun updateSyncingToSuccess(
        module: MochaModule,
        fromStatus: SyncStatus,
        toStatus: SyncStatus
    ) = internalTransition(module, fromStatus, toStatus)

    override suspend fun updateSyncingToFailure(
        module: MochaModule,
        fromStatus: SyncStatus,
        toStatus: SyncStatus
    ) = internalTransition(module, fromStatus, toStatus)

    // --- Router ---
    private suspend fun internalTransition(
        module: MochaModule,
        from: SyncStatus,
        to: SyncStatus
    )  {
        val affected = dao.transitionState(
            module = module,
            fromStatus = from,
            toStatus = to
        )

        if (affected == 0) {
            throw MochaException.Transient.Contention(
                "Contention on $module: Expected $from " +
                        "but found another state. Action aborted to prevent lost updates."
            )
        }
    }

    override suspend fun finalizeSync(
        module: MochaModule,
        syncId: String,
        newWatermark: String
    ) {
        val affected = dao.finalizeSyncSuccess(
            module = module,
            currentSyncId = syncId,
            watermark = newWatermark,
            now = Clock.System.now().toEpochMilliseconds()
        )

        if (affected == 0) {
            throw MochaException.Transient.Contention(
                "Failed to finalize sync for $module. " +
                        "The sync lock was lost or preempted."
            )
        }
    }

    override suspend fun bulkResetDirtyModules(): Int {
        return dao.bulkResetDirtyModules()
    }

    override suspend fun getDirtyModuleNames(): List<String> {
        return dao.getDirtyModuleNames()
    }

    override suspend fun getMetadataCount(): Int {
        return dao.getMetadataCount()
    }

    suspend fun getModuleMetadata(module: MochaModule): SyncMetadataEntity?  {
        return dao.getMetadataForModule(module)
    }

    override suspend fun ensureSeeded(): Int {
        return dao.ensureSeeded(MochaModule.entries.toList())
    }

    override suspend fun getGlobalMaxHlc(): String? {
        return dao.getGlobalMaxHlc()
    }

}