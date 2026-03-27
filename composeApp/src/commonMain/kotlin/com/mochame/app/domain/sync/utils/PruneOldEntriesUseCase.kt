package com.mochame.app.domain.sync.utils

import com.mochame.app.domain.sync.MutationLedgerMaintenance
import com.mochame.app.infrastructure.utils.DateTimeUtils

class PruneOldEntriesUseCase(
    private val ledgerMaintenance: MutationLedgerMaintenance,
    private val dateTimeUtils: DateTimeUtils
) {
    companion object {
        private const val DEFAULT_PRUNE_DAYS = 30
    }

    suspend operator fun invoke(): Int {
        val threshold = dateTimeUtils.getMillisForDaysAgo(DEFAULT_PRUNE_DAYS)

        return ledgerMaintenance.pruneOldSynced(threshold)
    }
}