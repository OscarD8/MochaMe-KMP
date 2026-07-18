package com.mochame.sync.domain.usecase

import co.touchlab.kermit.Logger
import com.mochame.utils.interfaces.TimeProvider
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.logger.withTimer
import kotlinx.coroutines.yield
import org.koin.core.annotation.Single
import kotlin.time.TimeSource

/**
 * On invocation, pruning will chunk based on a [LIMIT], yielding between
 * chunks. The cut-off is based on the provided [pruneDays] which defaults to
 * [DEFAULT_PRUNE_DAYS].
 *
 * The method responsible for the pruning process is [com.mochame.sync.data.SyncIntentDao.pruneOldSynced] -
 * defining that the cut-off is based on the [com.mochame.sync.spi.models.SyncIntent.createdAt] field, requiring
 * a status representing synchronization success provided by the server.
 */
@Single
internal class PruneIntentsUseCase(
    private val intentStore: SyncIntentMaintenanceStore,
    private val timeUtils: TimeProvider,
    private val pruneDays: Int = DEFAULT_PRUNE_DAYS,
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
        className = "MsPrune"
    )

    suspend operator fun invoke(): Int {
        val cutoff = timeUtils.getMillisForDaysAgo(pruneDays)

        val mark = TimeSource.Monotonic.markNow()
        var totalDeleted = 0
        var iterations = 0

        do {
            val deleted = intentStore.pruneOldSynced(cutoff, LIMIT)
            totalDeleted += deleted
            iterations++

            if (iterations % LOG_INTERVAL == 0) {
                logger.v { "Pruning in progress... $totalDeleted entries removed." }
            }

            if (deleted != LIMIT) break
            if (deleted > 0)  yield()

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