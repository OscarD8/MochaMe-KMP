package com.mochame.app.domain.repository.telemetry

/**
 * The unified contract for the Telemetry "Nervous System".
 * * By inheriting from specialized action interfaces, we maintain
 * strict separation of concerns while allowing a single implementation
 * to be injected via Koin.
 */
interface TelemetryRepository :
    IdentityActions,    // Managing the "Context" (Domains, Topics, Spaces)
    ObservationActions, // The act of logging "Moments"
    ChronicleActions,   // Querying historical data and streams
    AtmosphereActions   // Environmental context (Weather/Sensors)