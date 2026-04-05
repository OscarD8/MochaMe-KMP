package com.mochame.app.data.mappers


import com.mochame.app.data.local.room.entity.DailyContextEntity
import com.mochame.app.domain.bio.DailyContext
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.infrastructure.sync.HLC

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