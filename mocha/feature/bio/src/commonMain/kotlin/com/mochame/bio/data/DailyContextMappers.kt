package com.mochame.bio.data

import com.mochame.bio.domain.DailyContext
import com.mochame.sync.api.models.HLC

/**
 * Entity -> Domain
 * Used when reading from the database or receiving remote changes via SyncGateway.
 */
fun DailyContextEntity.toDomain() = DailyContext(
    id = id,
    hlc = HLC.parse(hlc),
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
    hlc = hlc.toString(),
    epochDay = epochDay,
    sleepHours = sleepHours,
    readinessScore = readinessScore,
    isNapped = isNapped,
    isDeleted = isDeleted,
    lastModified = lastModified
)