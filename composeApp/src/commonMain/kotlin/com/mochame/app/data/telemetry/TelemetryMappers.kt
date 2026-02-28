package com.mochame.app.data.telemetry

import com.mochame.app.domain.telemetry.Category
import com.mochame.app.database.entities.CategoryEntity
import kotlin.time.Clock

/**
 * Data Layer -> Domain Layer
 * Used when pulling from the database to show in the UI.
 */
fun CategoryEntity.toDomain(): Category {
    return Category(
        id = this.id,
        name = this.name,
        hexColor = this.hexColor
    )
}

/**
 * Domain Layer -> Data Layer
 * Used when saving a new or edited category from the UI.
 * We inject the lastModified timestamp here.
 */
fun Category.toEntity(
    lastModified: Long = Clock.System.now().toEpochMilliseconds()
): CategoryEntity {
    return CategoryEntity(
        id = this.id,
        name = this.name,
        hexColor = this.hexColor,
        isActive = true,
        lastModified = lastModified
    )
}