package com.mochame.app.data.local.room.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.mochame.app.domain.feature.telemetry.MomentClimate
import com.mochame.app.domain.feature.telemetry.MomentCore
import com.mochame.app.domain.feature.telemetry.MomentDetail
import com.mochame.app.domain.feature.telemetry.MomentMetadata


/**
 * The Relational Bridge.
 * This is NOT a table; it's a structural instruction for Room.
 */
data class MomentWithAttachments(
    @Embedded val moment: MomentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "momentId"
    )
    val attachments: List<MomentAttachmentEntity>
)

@Entity(
    tableName = "moments",
    foreignKeys = [
        ForeignKey(entity = DomainEntity::class, parentColumns = ["id"], childColumns = ["domainId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SpaceEntity::class, parentColumns = ["id"], childColumns = ["spaceId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index("domainId"), Index("topicId"), Index("spaceId"),
        Index("associatedEpochDay"), Index("timestamp"),
        Index("lastModified")
    ]
)
data class MomentEntity(
    @PrimaryKey val id: String,
    val domainId: String,
    val topicId: String?,
    val spaceId: String?,

    @Embedded val core: MomentCore,
    @Embedded val detail: MomentDetail,
    @Embedded val context: MomentClimate,
    @Embedded val metadata: MomentMetadata,
)

@Entity(
    tableName = "moment_attachments",
    foreignKeys = [
        ForeignKey(
            entity = MomentEntity::class,
            parentColumns = ["id"],
            childColumns = ["momentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("momentId"), Index("contentBlobId")]
)
data class MomentAttachmentEntity(
    @PrimaryKey val id: String,        // Local UUID
    val momentId: String,             // Link to the Moment
    val contentBlobId: String,        // SHA-256 Hash from BlobStore
    val mimeType: String,             // e.g., "video/mp4"
    val fileName: String,
    val fileSize: Long,
    val localUri: String?             // Temporary path for local UI display
)

@Entity(
    tableName = "domains",
    indices = [
        Index(value = ["name"], unique = true),
        Index("lastModified"), // Added for Sync Delta
    ]
)
data class DomainEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hexColor: String,
    val iconKey: String, // e.g., "ic_work", "ic_heart"
    val isActive: Boolean,
    val lastModified: Long,
)


@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = DomainEntity::class, // The Anchor
            parentColumns = ["id"],
            childColumns = ["domainId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["domainId", "name"], unique = true), // name must be unique within its domain
        Index("lastModified")
    ]
)
data class TopicEntity(
    @PrimaryKey val id: String,
    val domainId: String,
    val name: String,
    val isActive: Boolean,
    val lastModified: Long,
)


@Entity(
    tableName = "spaces",
    indices = [
        // Taxonomy Integrity: Prevents "Home" and "home" duplicates
        Index(value = ["name"], unique = true),
        // Sync & Analytical Integrity: Used for localized analysis windows
        Index("lastModified")
    ]
)
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val defaultBiophilia: Int?,
    val isControlled: Boolean,
    val isActive: Boolean,
    val lastModified: Long,
)