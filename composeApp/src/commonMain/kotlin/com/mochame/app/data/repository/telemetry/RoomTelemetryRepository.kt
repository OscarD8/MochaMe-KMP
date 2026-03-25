package com.mochame.app.data.repository.telemetry


import com.mochame.app.domain.telemetry.repositories.AnalyticsRepository
import com.mochame.app.domain.telemetry.repositories.ContextRepository
import com.mochame.app.domain.telemetry.repositories.MomentRepository
import com.mochame.app.domain.telemetry.repositories.TelemetryRepository


/*
This is Composition over Inheritance, even though it wears an "Interface" mask.

    Inheritance (The Old Way): If TelemetryRepositoryImpl inherited from MomentBridge, it would be stuck.
    In many languages, you can only inherit from one parent. You couldn't be both a MomentBridge AND a ContextBridge.

    Composition (Your Way): Your TelemetryRepositoryImpl HAS A MomentBridge and HAS A ContextBridge.

    The "By" Magic: This is "Interface Delegation." It allows the object to act as if it has inheritance
    (satisfying the "Is-A" contract) while actually using composition (the "Has-A" reality).

    This is cool

    Think of the TelemetryRepository as a Universal Remote Control.
    The remote doesn't know how to change the volume (that's the Speaker's job).
    The remote doesn't know how to change the channel (that's the TV's job).
    But the remote delegates your button press to the right component.
    To the user (the ViewModel), the remote is the volume and channel controller.
 */
internal class RoomTelemetryRepository(
    private val context: ContextRepository,
    private val moment: MomentRepository,
    private val analytics: AnalyticsRepository
) : TelemetryRepository,
    ContextRepository by context,
    MomentRepository by moment,
    AnalyticsRepository by analytics