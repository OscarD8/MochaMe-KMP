package com.mochame.app.domain.repository.telemetry

/**
 * The unified contract for the Telemetry "Nervous System".
 * * By inheriting from specialized action interfaces, we maintain
 * strict separation of concerns while allowing a single implementation
 * to be injected via Koin.
 */
interface TelemetryRepository :
    ContextRepository,    // Managing the "Context" (Domains, Topics, Spaces)
    MomentRepository, // The act of logging "Moments"
    AnalyticsRepository,   // Querying historical data and streams
    EnvironmentRepository   // Environmental context (Weather/Sensors)