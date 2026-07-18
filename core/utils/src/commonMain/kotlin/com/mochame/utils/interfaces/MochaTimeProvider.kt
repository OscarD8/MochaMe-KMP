package com.mochame.utils.interfaces

import kotlin.time.Instant

/**
 * Domain-specific functionality on 4 AM cutoff.
 */
interface MochaTimeProvider : TimeProvider {
    fun getMochaDay(): Long
    fun calculateMochaEpochDay(instant: Instant): Long
    override fun getMillisForDaysAgo(days: Int): Long
}