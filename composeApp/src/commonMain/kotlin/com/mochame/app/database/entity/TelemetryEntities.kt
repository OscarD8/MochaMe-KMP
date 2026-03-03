package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moments",
    foreignKeys = [
        ForeignKey(entity = DomainEntity::class, parentColumns = ["id"], childColumns = ["domainId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SpaceEntity::class, parentColumns = ["id"], childColumns = ["spaceId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("domainId"), Index("spaceId"), Index("associatedEpochDay")]
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
    indices = [Index(value = ["name"], unique = true)] // Category names should be unique
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
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.RESTRICT // THE SHIELD: No orphaned moments
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["topicId"]) // THE ACCELERATOR: Fast usage checks
    ]
)
data class TopicEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val isActive: Boolean,
    val lastModified: Long
)

@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String, // e.g., "ic_home", "ic_cafe", "ic_park"

    // --- The Atmospheric Baseline ---
    // Users can define the "Default Atmosphere" of a space
    val defaultBiophilia: Int?,     // 1-5
    val noiseLevel: Int?,           // 1-5 (Quiet to Chaotic)
    val isControlled: Boolean,      // Private vs. Public

    val isActive: Boolean,
    val lastModified: Long
)