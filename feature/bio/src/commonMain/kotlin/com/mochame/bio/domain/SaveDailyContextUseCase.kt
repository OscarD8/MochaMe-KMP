package com.mochame.bio.domain

import com.mochame.utils.cli.Update
import com.mochame.utils.cli.resolve
import org.koin.core.annotation.Factory

@Factory
class SaveDailyContextUseCase(
    private val repository: DailyContextRepository
) {
    suspend operator fun invoke(
        epochDay: Long,
        sleepHours: Update<Double> = Update.Unchanged,
        readinessScore: Update<Int> = Update.Unchanged,
        isNapped: Update<Boolean> = Update.Unchanged
    ): Result<Unit> = runCatching {
        val existing = repository.getContext(epochDay)

        val updatedEntity = DailyContext(
            id = epochDay,
            sleepHours = sleepHours.resolve(existing?.sleepHours),
            readinessScore = readinessScore.resolve(existing?.readinessScore),
            isNapped = isNapped.resolve(existing?.isNapped)
        )

        repository.upsertContext(updatedEntity)
    }
}
