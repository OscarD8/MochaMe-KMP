package com.mochame.app.data.mapper


import com.mochame.app.core.HLC
import com.mochame.app.core.HlcParseException
import com.mochame.app.database.entity.DailyContextEntity
import com.mochame.app.domain.model.DailyContext

/**
 * Entity -> Domain
 * Used when reading from the database or receiving remote changes via SyncGateway.
 */
fun DailyContextEntity.toDomain(): DailyContext {
    return try {
        DailyContext (
            id = id,
            hlc = HLC.parse(hlc),
            epochDay = epochDay,
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            isNapped = isNapped,
            isDeleted = isDeleted,
            lastModified = lastModified
        )
    } catch (e: HlcParseException) {
        throw e
    }
}

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