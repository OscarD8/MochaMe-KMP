package com.mochame.app.data.mapper

import com.mochame.app.database.entity.CategoryEntity
import com.mochame.app.database.entity.MomentEntity
import com.mochame.app.database.entity.TopicEntity
import com.mochame.app.domain.model.Category
import com.mochame.app.domain.model.Moment
import com.mochame.app.domain.model.Topic

// --- MOMENT MAPPERS ---
internal fun MomentEntity.toDomain(): Moment = Moment(
    id = id,
    timestamp = timestamp,
    associatedEpochDay = associatedEpochDay,
    categoryId = categoryId,
    topicId = topicId,
    durationMinutes = durationMinutes,
    satisfactionScore = satisfactionScore,
    energyScore = energyScore,
    moodScore = moodScore,
    note = note,
    lastModified = lastModified
)

internal fun Moment.toEntity(): MomentEntity = MomentEntity(
    id = id,
    timestamp = timestamp,
    associatedEpochDay = associatedEpochDay,
    categoryId = categoryId,
    topicId = topicId,
    durationMinutes = durationMinutes,
    satisfactionScore = satisfactionScore,
    energyScore = energyScore,
    moodScore = moodScore,
    note = note,
    lastModified = lastModified
)

// --- CATEGORY MAPPERS ---
internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    hexColor = hexColor,
    isActive = isActive,
    lastModified = lastModified
)

internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    hexColor = hexColor,
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