package com.mochame.app.data.mapper

import com.mochame.app.database.entity.DomainEntity
import com.mochame.app.database.entity.MomentCoreEntity
import com.mochame.app.database.entity.MomentEntity
import com.mochame.app.database.entity.SpaceEntity
import com.mochame.app.database.entity.TopicEntity
import com.mochame.app.domain.model.telemetry.Domain
import com.mochame.app.domain.model.telemetry.Moment
import com.mochame.app.domain.model.telemetry.MomentCore
import com.mochame.app.domain.model.telemetry.Mood
import com.mochame.app.domain.model.telemetry.Space
import com.mochame.app.domain.model.telemetry.Topic

// --- MOMENT MAPPERS ---
fun MomentEntity.toDomain(): Moment {
    return Moment(
        id = id,
        domainId = domainId,
        topicId = topicId,
        spaceId = spaceId,
        core = MomentCore(
            satisfactionScore = core.satisfactionScore,
            mood = core.mood, // PAD mapping happens here
            energyDelta = core.energyDelta,
            intensityScale = core.intensityScale
        ),
        detail = detail,
        context = context,
        metadata = metadata
    )
}

fun Moment.toEntity(): MomentEntity {
    return MomentEntity(
        id = id,
        domainId = domainId,
        topicId = topicId,
        spaceId = spaceId,
        core = MomentCoreEntity(
            satisfactionScore = core.satisfactionScore,
            mood = core.mood, // Convert back to string for DB
            energyDelta = core.energyDelta,
            intensityScale = core.intensityScale
        ),
        detail = detail,
        context = context,
        metadata = metadata
    )
}

// --- CATEGORY MAPPERS ---
fun DomainEntity.toDomain(): Domain = Domain(
    id = id,
    name = name,
    hexColor = hexColor,
    iconKey = iconKey,
    isActive = isActive,
    lastModified = lastModified
)

fun Domain.toEntity(): DomainEntity = DomainEntity(
    id = id,
    name = name,
    hexColor = hexColor,
    iconKey = iconKey,
    isActive = isActive,
    lastModified = lastModified
)


// --- TOPIC MAPPERS ---
internal fun TopicEntity.toDomain(): Topic = Topic(
    id = id,
    domainId = domainId,
    name = name,
    isActive = isActive,
    lastModified = lastModified
)

internal fun Topic.toEntity(): TopicEntity = TopicEntity(
    id = id,
    domainId = domainId,
    name = name,
    isActive = isActive,
    lastModified = lastModified
)


// --- SPACE MAPPERS ---
fun SpaceEntity.toDomain(): Space = Space(
    id = id,
    name = name,
    iconKey = iconKey,
    defaultBiophilia = defaultBiophilia,
    isControlled = isControlled,
    isActive = isActive,
    lastModified = lastModified
)

fun Space.toEntity(): SpaceEntity = SpaceEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    defaultBiophilia = defaultBiophilia,
    isControlled = isControlled,
    isActive = isActive,
    lastModified = lastModified
)