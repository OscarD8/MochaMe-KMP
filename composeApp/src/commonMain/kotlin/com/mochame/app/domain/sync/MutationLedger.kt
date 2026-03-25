package com.mochame.app.domain.sync

import com.mochame.app.data.local.room.entity.MutationEntryEntity

interface MutationLedger {
    suspend fun getPending(candidateKey: String, entityType: String): MutationEntryEntity?
    suspend fun upsert(entry: MutationEntryEntity)
    suspend fun deleteByHlc(hlc: String)
}