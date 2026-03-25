package com.mochame.app.domain.telemetry.repositories

/*
"Composite Interface." It doesn't add new logic; it just defines a required set of capabilities.
By passing this "single composition" (the Shell) up to the ViewModel,
you keep the ViewModel's constructor clean. Instead of the ViewModel
needing 5 different repos, it needs one "Cell" that happens to have 5 "Ribosomes" inside it.
 */
interface TelemetryRepository :
    ContextRepository,    // Managing the "Context" (Domains, Topics, Spaces)
    MomentRepository, // The act of logging "Moments"
    AnalyticsRepository,   // Querying historical data and streams
    EnvironmentRepository   // Environmental context (Weather/Sensors)