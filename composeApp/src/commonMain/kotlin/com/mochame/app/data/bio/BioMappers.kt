package com.mochame.app.data.bio

import com.mochame.app.domain.bio.DailyContext
import com.mochame.app.database.entities.DailyContextEntity
import kotlin.time.Clock

/**
 * Data Layer -> Domain Layer
 * Extracts only the fields the UI/Logic needs.
 */
fun DailyContextEntity.toDomain(): DailyContext {
    return DailyContext(
        id = this.id,
        epochDay = this.epochDay,
        sleepHours = this.sleepHours
    )
}

/**
 * Domain Layer -> Data Layer
 * Bridges the gap between the clean model and the Room entity.
 */
fun DailyContext.toEntity(
    lastModified: Long = Clock.System.now().toEpochMilliseconds()
): DailyContextEntity {
    return DailyContextEntity(
        id = this.id,
        epochDay = this.epochDay,
        sleepHours = this.sleepHours,
        lastModified = lastModified
    )
}