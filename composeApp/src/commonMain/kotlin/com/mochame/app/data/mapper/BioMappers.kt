package com.mochame.app.data.mapper


import com.mochame.app.database.entity.DailyContextEntity
import com.mochame.app.domain.model.DailyContext
import kotlin.time.Clock

/**
 * Data Layer -> Domain Layer
 * Extracts only the fields the UI/Logic needs.
 */
fun DailyContextEntity.toDomain(): DailyContext {
    return DailyContext(
        id = this.id,
        epochDay = this.epochDay,
        sleepHours = this.sleepHours,
        readinessScore = this.readinessScore,
        lastModified = this.lastModified
    )
}

/**
 * Domain Layer -> Data Layer
 * Bridges the gap between the clean model and the Room entity.
 */
fun DailyContext.toEntity(): DailyContextEntity {
    return DailyContextEntity(
        id = this.id,
        epochDay = this.epochDay,
        sleepHours = this.sleepHours,
        readinessScore = this.readinessScore,
        lastModified = this.lastModified
    )
}