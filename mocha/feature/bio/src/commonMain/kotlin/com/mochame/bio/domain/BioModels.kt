package com.mochame.bio.domain

import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.LocalFirstEntity

/**
 * Represents the biological capacity and readiness for a single day.
 * This acts as the framing context for all telemetry logged during this period.
 */
data class DailyContext(
    override val id: String,
    override val hlc: HLC = HLC.EMPTY,
    val epochDay: Long,
    val sleepHours: Double,
    val readinessScore: Int,
    val isNapped: Boolean = false,
    val isDeleted: Boolean = false,
    val lastModified: Long = 0L
) : LocalFirstEntity<DailyContext> {

    override fun withHlc(hlc: HLC): DailyContext = copy(hlc = hlc)
    override fun withPhysicalTime(time: Long): DailyContext = copy(lastModified = time)
}