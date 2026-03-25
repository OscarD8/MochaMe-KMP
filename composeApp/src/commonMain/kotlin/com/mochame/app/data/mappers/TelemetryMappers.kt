package com.mochame.app.data.mappers

import com.mochame.app.data.local.room.entity.DomainEntity
import com.mochame.app.data.local.room.entity.MomentEntity
import com.mochame.app.data.local.room.entity.SpaceEntity
import com.mochame.app.data.local.room.entity.TopicEntity
import com.mochame.app.domain.telemetry.Domain
import com.mochame.app.domain.telemetry.Moment
import com.mochame.app.domain.telemetry.Space
import com.mochame.app.domain.telemetry.Topic

// --- MOMENT MAPPERS ---
fun MomentEntity.toDomain(): Moment {
    return Moment(
        id = id,
        domainId = domainId,
        topicId = topicId,
        spaceId = spaceId,
        core = core,
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
        core = core,
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