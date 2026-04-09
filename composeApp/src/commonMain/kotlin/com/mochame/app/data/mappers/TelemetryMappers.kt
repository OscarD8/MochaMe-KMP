package com.mochame.app.data.mappers

import com.mochame.app.data.local.room.entities.DomainEntity
import com.mochame.app.data.local.room.entities.MomentAttachmentEntity
import com.mochame.app.data.local.room.entities.MomentEntity
import com.mochame.app.data.local.room.entities.MomentWithAttachments
import com.mochame.app.data.local.room.entities.SpaceEntity
import com.mochame.app.data.local.room.entities.TopicEntity
import com.mochame.app.domain.feature.telemetry.Domain
import com.mochame.app.domain.feature.telemetry.Moment
import com.mochame.app.domain.feature.telemetry.MomentAttachment
import com.mochame.app.domain.feature.telemetry.Space
import com.mochame.app.domain.feature.telemetry.Topic

// --- MOMENT MAPPERS ---
fun MomentEntity.toDomain(attachments: List<MomentAttachment> = emptyList()): Moment {
    return Moment(
        id = id,
        domainId = domainId,
        topicId = topicId,
        spaceId = spaceId,
        core = core,
        detail = detail,
        context = context,
        metadata = metadata,
        attachments = attachments
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

fun MomentWithAttachments.toDomain(): Moment {
    val domainAttachments = attachments.map { it.toDomain() }
    return moment.toDomain(domainAttachments)
}

fun MomentAttachmentEntity.toDomain(): MomentAttachment {
    return MomentAttachment(
        id = id,
        contentBlobId = contentBlobId,
        mimeType = mimeType,
        fileName = fileName,
        fileSize = fileSize,
        localUri = localUri
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