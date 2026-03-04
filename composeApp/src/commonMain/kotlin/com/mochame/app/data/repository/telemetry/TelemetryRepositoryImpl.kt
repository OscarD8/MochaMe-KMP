package com.mochame.app.data.repository.telemetry


import com.mochame.app.domain.repository.telemetry.ChronicleActions
import com.mochame.app.domain.repository.telemetry.IdentityActions
import com.mochame.app.domain.repository.telemetry.ObservationActions
import com.mochame.app.domain.repository.telemetry.TelemetryRepository


// The Repository is now a pure "Router"
internal class TelemetryRepositoryImpl(
    private val identity: IdentityActions,
    private val observation: ObservationActions,
    private val chronicle: ChronicleActions
) : TelemetryRepository,
    IdentityActions by identity,
    ObservationActions by observation,
    ChronicleActions by chronicle