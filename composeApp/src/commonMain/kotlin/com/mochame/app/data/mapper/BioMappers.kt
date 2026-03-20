package com.mochame.app.data.mapper


import com.mochame.app.database.entity.DailyContextEntity
import com.mochame.app.domain.model.DailyContext
import kotlin.time.Clock

/**
 * Entity -> Domain
 * Used when reading from the database or receiving remote changes via SyncGateway.
 */
fun DailyContextEntity.toDomain() = DailyContext(
    id = id,
    hlc = hlc,
    epochDay = epochDay,
    sleepHours = sleepHours,
    readinessScore = readinessScore,
    isNapped = isNapped,
    isDeleted = isDeleted,
    lastModified = lastModified
)

/**
 * Domain -> Entity
 * Used when persisting local changes or resolving remote conflicts in the DAO.
 */
fun DailyContext.toEntity() = DailyContextEntity(
    id = id,
    hlc = hlc,
    epochDay = epochDay,
    sleepHours = sleepHours,
    readinessScore = readinessScore,
    isNapped = isNapped,
    isDeleted = isDeleted,
    lastModified = lastModified
)