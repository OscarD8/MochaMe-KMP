package com.mochame.contract.providers

import kotlin.time.Instant

interface DateTimeProvider {
    /** The actual system "now" */
    fun now(): Instant

    /** Returns the current "Mocha Day" (4 AM threshold) */
    fun getMochaDay(): Long

    /** Calculates which Mocha Day a specific instant belongs to */
    fun calculateMochaEpochDay(instant: Instant): Long

    /** Threshold for pruning logic (04:00 AM of X days ago) */
    fun getMillisForDaysAgo(days: Int): Long

    /** UI helper: Monday, Oct 12 */
    fun formatEpochDay(epochDay: Long): String

    /** Calm-Tech Heuristic: Is it daytime? */
    fun isDaylight(instant: Instant): Boolean
}