package com.mochame.sync.domain.usecase

import co.touchlab.kermit.Logger
import com.mochame.sync.domain.stores.MutationLedgerMaintenance
import com.mochame.utils.DateTimeUtils
import com.mochame.utils.logger.LogTags
import com.mochame.utils.logger.withTags
import com.mochame.utils.logger.withTimer
import kotlinx.coroutines.yield
import kotlin.time.TimeSource

class PruneOldEntriesUseCase(
    private val ledgerMaintenance: MutationLedgerMaintenance,
    private val dateTimeUtils: DateTimeUtils,
    logger: Logger
) {
    companion object {
        private const val DEFAULT_PRUNE_DAYS = 30
        private const val LIMIT = 100
        private const val LOG_INTERVAL = 50
    }

    private val logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.SYNC,
        className = "LedgerPruner"
    )

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