package com.mochame.app.data.mapper

import com.mochame.app.database.entity.DomainEntity
import com.mochame.app.database.entity.MomentEntity
import com.mochame.app.database.entity.TopicEntity
import com.mochame.app.domain.model.Domain
import com.mochame.app.domain.model.Moment
import com.mochame.app.domain.model.Topic

// --- MOMENT MAPPERS ---
fun MomentEntity.toDomain(): Moment = Moment(
    id = id,
    domainId = domainId,
    satisfactionScore = satisfactionScore,
    moodScore = moodScore,
    energyScore = energyScore,
    note = note,
    topicId = topicId,
    spaceId = spaceId,
    isFocusTime = isFocusTime,
    socialScale = socialScale,
    energyDrain = energyDrain,
    biophiliaScale = biophiliaScale,
    durationMinutes = durationMinutes,
    isDaylight = isDaylight,
    cloudDensity = cloudDensity,
    isPrecipitating = isPrecipitating,
    timestamp = timestamp,
    associatedEpochDay = associatedEpochDay,
    lastModified = lastModified
)

fun Moment.toEntity(): MomentEntity = MomentEntity(
    id = id,
    domainId = domainId,
    satisfactionScore = satisfactionScore,
    moodScore = moodScore,
    energyScore = energyScore,
    note = note,
    topicId = topicId,
    spaceId = spaceId,
    isFocusTime = isFocusTime,
    socialScale = socialScale,
    energyDrain = energyDrain,
    biophiliaScale = biophiliaScale,
    durationMinutes = durationMinutes,
    isDaylight = isDaylight,
    cloudDensity = cloudDensity,
    isPrecipitating = isPrecipitating,
    timestamp = timestamp,
    associatedEpochDay = associatedEpochDay,
    lastModified = lastModified
)

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
    parentId = parentId,
    name = name,
    isActive = isActive,
    lastModified = lastModified
)

internal fun Topic.toEntity(): TopicEntity = TopicEntity(
    id = id,
    parentId = parentId,
    name = name,
    isActive = isActive,
    lastModified = lastModified
)