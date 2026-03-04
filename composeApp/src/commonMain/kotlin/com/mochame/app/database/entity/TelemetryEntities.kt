package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moments",
    foreignKeys = [
        ForeignKey(entity = DomainEntity::class, parentColumns = ["id"], childColumns = ["domainId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SpaceEntity::class, parentColumns = ["id"], childColumns = ["spaceId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index("domainId"),
        Index("topicId"),
        Index("spaceId"),
        Index("associatedEpochDay"),
        Index("timestamp"),    // Added for UI Feed performance
        Index("lastModified")  // Added for Sync Delta performance
    ]
)
data class MomentEntity(
    @PrimaryKey val id: String,
    val domainId: String,

    // Pulse
    val satisfactionScore: Int,
    val moodScore: Int,
    val energyScore: Int,

    // Enrichment
    val note: String?,
    val topicId: String?,
    val spaceId: String?,
    val isFocusTime: Boolean?,
    val socialScale: Int?,
    val energyDrain: Int?,
    val biophiliaScale: Int?,
    val durationMinutes: Int?,

    // Weather Context
    val isDaylight: Boolean?,
    val cloudDensity: Int?,
    val isPrecipitating: Boolean?,

    // System
    val timestamp: Long,
    val associatedEpochDay: Long,
    val lastModified: Long
)

@Entity(
    tableName = "domains",
    indices = [
        Index(value = ["name"], unique = true), // Category names should be unique
        Index("lastModified") // Added for Sync Delta
    ]
)
data class DomainEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hexColor: String,
    val iconKey: String, // e.g., "ic_work", "ic_heart"
    val isActive: Boolean,
    val lastModified: Long
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = DomainEntity::class, // The Anchor
            parentColumns = ["id"],
            childColumns = ["domainId"],
            onDelete = ForeignKey.RESTRICT // If the Domain has Topics, you can't delete the Domain
        )
    ],
    indices = [
        Index(value = ["domainId", "name"], unique = true), // name must be unique within its domain
        Index("lastModified") // Added for Sync Delta
    ]
)
data class TopicEntity(
    @PrimaryKey val id: String,
    val domainId: String,
    val name: String,
    val isActive: Boolean,
    val lastModified: Long
)


@Entity(
    tableName = "spaces",
    indices = [
        // Taxonomy Integrity: Prevents "Home" and "home" duplicates
        Index(value = ["name"], unique = true),
        // Sync & Analytical Integrity: Used for localized analysis windows
        Index(value = ["lastModified"])
    ]
)
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val defaultBiophilia: Int?,
    val isControlled: Boolean,
    val isActive: Boolean,
    val lastModified: Long
)