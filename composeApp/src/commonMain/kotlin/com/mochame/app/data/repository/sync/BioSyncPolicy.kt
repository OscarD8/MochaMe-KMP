package com.mochame.app.data.repository.sync

import com.mochame.app.data.local.room.entity.DailyContextEntity
import com.mochame.app.domain.system.sync.SyncPolicy

class BioSyncPolicy : SyncPolicy<DailyContextEntity> {

    override fun resolveConflict(local: DailyContextEntity, remote: DailyContextEntity): DailyContextEntity {
        // Simple Last-Write-Wins (LWW) based on the 2026 HLC or timestamp
        return if (remote.lastModified > local.lastModified) {
            // We preserve the local Identity (UUID) but take the remote values
            remote.copy(epochDay = local.epochDay)
        } else {
            local
        }
    }

    override fun getIdentity(entity: DailyContextEntity): String = entity.epochDay.toString()

    override fun getVersion(entity: DailyContextEntity): Long = entity.lastModified
}