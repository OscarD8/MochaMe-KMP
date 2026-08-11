package com.mochame.bio.domain

import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.common.TriState

/**
 * Represents the biological capacity and readiness for a single day.
 * This acts as the framing context for all telemetry logged during this period.
 */
data class DailyContext(
    override val id: Long,
    override val hlc: HLC = HLC.EMPTY,
    override val isDeleted: Boolean = false,
    override val lastModified: Long = 0L,
    override val fieldHlcs: ByteArray = ByteArray(0),
    val sleepHours: Double,
    val readinessScore: Int,
    val isNapped: TriState = TriState.UNSET,
) : LocalFirstEntity<DailyContext> {

    override fun withHlc(hlc: HLC): DailyContext = copy(hlc = hlc, lastModified = hlc.ts)
    override fun withFieldHlcs(blob: ByteArray) = copy(fieldHlcs = blob)
    override fun markDeleted() = copy(isDeleted = true)
}