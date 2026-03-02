package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moments",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["categoryId"]), // The performance booster
        Index(value = ["topicId"])     // Good practice to index this too
    ]
)
data class MomentEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val associatedEpochDay: Long,
    val categoryId: String,
    val topicId: String?,
    val durationMinutes: Int,
    val satisfactionScore: Int,
    val energyScore: Int,
    val moodScore: Int,
    val note: String,
    val lastModified: Long // Must be set explicitly by Repository
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)] // Category names should be unique
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hexColor: String,
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