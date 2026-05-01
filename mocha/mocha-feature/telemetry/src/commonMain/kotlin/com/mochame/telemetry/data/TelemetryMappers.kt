package com.mochame.telemetry.data

import com.mochame.telemetry.domain.Domain
import com.mochame.telemetry.domain.Moment
import com.mochame.telemetry.domain.MomentAttachment
import com.mochame.telemetry.domain.Space
import com.mochame.telemetry.domain.Topic

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