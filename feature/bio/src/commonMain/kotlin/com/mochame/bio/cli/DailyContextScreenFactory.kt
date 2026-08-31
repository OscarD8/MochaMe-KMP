package com.mochame.bio.cli

import com.mochame.bio.domain.DailyContextRepository
import com.mochame.bio.domain.SaveDailyContextUseCase
import com.mochame.utils.interfaces.MochaTimeUtils
import org.koin.core.annotation.Single

@Single
class DailyContextCliScreenFactory(
    private val repository: DailyContextRepository,
    private val saveUseCase: SaveDailyContextUseCase,
    private val timeProvider: MochaTimeUtils
) {
    fun create(): DailyContextCliScreen = DailyContextCliScreen(
        repository = repository,
        saveUseCase = saveUseCase,
        timeProvider = timeProvider
    )
}