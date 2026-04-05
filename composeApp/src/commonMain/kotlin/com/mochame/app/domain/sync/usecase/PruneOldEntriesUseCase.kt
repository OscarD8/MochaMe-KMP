package com.mochame.app.domain.sync.usecase

import co.touchlab.kermit.Logger
import com.mochame.app.domain.sync.MutationLedgerMaintenance
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.infrastructure.utils.withTimer
import kotlinx.coroutines.yield
import kotlin.time.TimeSource

class PruneOldEntriesUseCase(
    private val ledgerMaintenance: MutationLedgerMaintenance,
    private val dateTimeUtils: DateTimeUtils,
    private val logger: Logger
) {
    companion object {
        private const val DEFAULT_PRUNE_DAYS = 30
        private const val LIMIT = 100
        private const val LOG_INTERVAL = 50
    }

    suspend operator fun invoke(): Int {
        val cutoff = dateTimeUtils.getMillisForDaysAgo(DEFAULT_PRUNE_DAYS)

        val mark = TimeSource.Monotonic.markNow()
        var totalDeleted = 0
        var iterations = 0

        do {
            val deleted = ledgerMaintenance.pruneOldSynced(cutoff, LIMIT)
            totalDeleted += deleted
            iterations++

            if (iterations % LOG_INTERVAL == 0) {
                logger.v { "Pruning in progress... $totalDeleted entries removed." }
            }

            if (deleted > 0) yield()

        } while (deleted > 0)

        if (totalDeleted > 0) {
            logger.i {
                "Prune Complete | Total: $totalDeleted | Chunks: $iterations"
                    .withTimer(mark)
            }
        }

        return totalDeleted
    }
}