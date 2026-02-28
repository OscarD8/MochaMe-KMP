package com.mochame.app.domain.bio

data class DailyContext(
    val id: String,
    val epochDay: Long,
    val sleepHours: Double
)