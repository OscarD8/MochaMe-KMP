package com.mochame.app.domain.model

/**
 * A specific moment of logging.
 * Renamed from WorkSession to reflect a calmer, flexible philosophy.
 */
data class Moment(
    val id: String, // UUID
    val timestamp: Long,
    val associatedEpochDay: Long, // Biologically attributed day
    val categoryId: String,
    val topicId: String?, // Nullable for general logging
    val durationMinutes: Int,
    val satisfactionScore: Int, // "How did it feel?"
    val energyScore: Int,
    val moodScore: Int,
    val note: String,
    val lastModified: Long
)

data class Category(
    val id: String,
    val name: String,
    val hexColor: String,
    val isActive: Boolean,
    val lastModified: Long
)

data class Topic(
    val id: String,
    val parentId: String?,
    val name: String,
    val isActive: Boolean,
    val lastModified: Long
)