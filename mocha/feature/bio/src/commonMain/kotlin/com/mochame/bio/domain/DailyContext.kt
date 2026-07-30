package com.mochame.bio.domain

import com.mochame.sync.api.models.HLC
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.common.TriState

/**
 * Represents the biological capacity and readiness for a single day.
 * This acts as the framing context for all telemetry logged during this period.
 */
data class DailyContext(
    override val id: String,
    override val hlc: HLC = HLC.EMPTY,
    override val isDeleted: Boolean = false,
    override val lastModified: Long,
    val epochDay: Long,
    val sleepHours: Double,
    val readinessScore: Int,
    val isNapped: TriState = TriState.UNSET,
) : LocalFirstEntity<DailyContext> {

    override fun withHlc(hlc: HLC): DailyContext = copy(hlc = hlc)
    override fun markDeleted(): DailyContext = copy(isDeleted = true)
}