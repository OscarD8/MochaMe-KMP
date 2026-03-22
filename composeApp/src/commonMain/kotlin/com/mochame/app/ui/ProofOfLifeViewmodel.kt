package com.mochame.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.model.telemetry.Domain
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.repository.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.emptyList
import kotlin.random.Random
import kotlin.time.Clock

class ProofOfLifeViewModel(
    private val telemetryRepo: TelemetryRepository,
    private val bioRepo: BioRepository // Injected via Koin
) : ViewModel() {

    // Observe Telemetry (The numerator)
    val categories = telemetryRepo.getActiveDomains()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Observe Bio Data (The denominator)
    // For proof-of-life, we'll observe a fixed epochDay (e.g., Day 0)
    val dailyContext = bioRepo.observeContext(1000L)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Initializes the "Biological Cup" for the current day.
     * * This method triggers the 4:00 AM anchor logic. If a context already
     * exists for this biological day, it will be updated; otherwise,
     * a new stable anchor is born.
     */
    fun initializeDailyContext(sleepHours: Double, readinessScore: Int) {
        viewModelScope.launch {
            try {
                // The Repository handles the Mutex and ID stability
                bioRepo.initializeDay(
                    sleepHours = sleepHours,
                    readinessScore = readinessScore
                )

                // Optional: Log an automatic "System Moment" to mark the start
                // of the day in the Telemetry timeline.
                /* telemetryRepository.logMoment(...)
                */

            } catch (e: Exception) {
                // Handle 2026 local-persistence errors (e.g., Disk Full)
                // In a Calm-Tech app, we'd emit a specific 'Error' state here.
            }
        }
    }

    fun addTestData() {
        viewModelScope.launch {
            // 1. Add a Telemetry Category
            val catId = Random.nextInt(1000).toString()
            telemetryRepo.upsertDomain(
                Domain(
                    id = catId, name = "Latte $catId", hexColor = "#6F4E37",
                    iconKey = "",
                    isActive = true,
                    lastModified = Clock.System.now().toEpochMilliseconds()
                )
            )

            // 2. Add/Update Bio Context for "Day 0"
            // This proves the 'Upsert' logic works in Room KMP
            bioRepo.initializeDay(
                sleepHours = 5.0,
                readinessScore = 9,
                isNapped = false
            )
        }
    }
}