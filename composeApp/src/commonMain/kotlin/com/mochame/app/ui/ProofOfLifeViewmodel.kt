package com.mochame.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochame.app.domain.model.Category
import com.mochame.app.database.entity.DailyContext
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.repository.TelemetryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.emptyList
import kotlin.random.Random

class ProofOfLifeViewModel(
    private val telemetryRepo: TelemetryRepository,
    private val bioRepo: BioRepository // Injected via Koin
) : ViewModel() {

    // Observe Telemetry (The numerator)
    val categories = telemetryRepo.observeActiveCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Observe Bio Data (The denominator)
    // For proof-of-life, we'll observe a fixed epochDay (e.g., Day 0)
    val dailyContext = bioRepo.getContextForDay(0)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun addTestData() {
        viewModelScope.launch {
            // 1. Add a Telemetry Category
            val catId = Random.nextInt(1000).toString()
            telemetryRepo.saveCategory(
                Category(id = catId, name = "Latte $catId", hexColor = "#6F4E37")
            )

            // 2. Add/Update Bio Context for "Day 0"
            // This proves the 'Upsert' logic works in Room KMP
            bioRepo.saveContext(
                DailyContext(
                    id = "initial_proof",
                    epochDay = 0,
                    sleepHours = 7.5 + Random.nextDouble() // Randomize to see UI update
                )
            )
        }
    }
}