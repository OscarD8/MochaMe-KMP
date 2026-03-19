package com.mochame.app.data.repository.telemetry


import com.mochame.app.domain.repository.telemetry.AnalyticsRepository
import com.mochame.app.domain.repository.telemetry.ContextRepository
import com.mochame.app.domain.repository.telemetry.MomentRepository
import com.mochame.app.domain.repository.telemetry.TelemetryRepository


// The Repository is now a pure "Router"
internal class TelemetryRepositoryImpl(
    private val context: ContextRepository,
    private val moment: MomentRepository,
    private val analytics: AnalyticsRepository
) : TelemetryRepository,
    ContextRepository by context,
    MomentRepository by moment,
    AnalyticsRepository by analytics