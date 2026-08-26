package com.mochame.bio.domain

import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.models.LocalFirstEntity
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Represents the biological capacity and readiness for a single day.
 * This acts as the framing context for all telemetry logged during this period.
 */
data class DailyContext(
    override val id: Long, // The Epoch Day
    override val hlc: HLC = HLC.EMPTY,
    override val isDeleted: Boolean = false,
    override val createdAt: Instant = Clock.System.now(),
    override val lastModified: Long = 0L,
    override val fieldHlcs: ByteArray = ByteArray(0),
    val sleepHours: Double? = null,
    val readinessScore: Int? = null,
    val isNapped: Boolean? = null,
) : LocalFirstEntity<DailyContext> {

    override fun withHlcMetadata(hlc: HLC, fieldBlob: ByteArray): DailyContext =
        copy(hlc = hlc, lastModified = hlc.ts, fieldHlcs = fieldBlob)

    override fun withDeleteState(state: Boolean) = copy(isDeleted = state)

    override fun withSyncHeader(
        hlc: HLC,
        lastModified: Long,
        createdAt: Instant,
        isDeleted: Boolean,
        fieldHlcs: ByteArray
    ): DailyContext = copy(
        hlc = hlc,
        lastModified = lastModified,
        createdAt = createdAt,
        isDeleted = isDeleted,
        fieldHlcs = fieldHlcs
    )
}