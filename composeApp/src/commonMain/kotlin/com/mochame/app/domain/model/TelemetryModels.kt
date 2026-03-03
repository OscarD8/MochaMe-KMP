package com.mochame.app.domain.model

/**
 * A specific moment of logging.
 * Renamed from WorkSession to reflect a calmer, flexible philosophy.
 */
data class Moment(
    val id: String,
    val domainId: String,

    // The Pulse (Mandatory)
    val satisfactionScore: Int,
    val moodScore: Int,
    val energyScore: Int,

    // Enrichment (Optional)
    val note: String?,
    val topicId: String?,
    val spaceId: String?,
    val isFocusTime: Boolean?,
    val socialScale: Int?,
    val energyDrain: Int?,
    val biophiliaScale: Int?,
    val durationMinutes: Int?,

    // Weather Context (Optional - Injected via Background Metabolism)
    val isDaylight: Boolean?,
    val cloudDensity: Int?,
    val isPrecipitating: Boolean?,

    // System Metadata
    val timestamp: Long,
    val associatedEpochDay: Long,
    val lastModified: Long
)

data class Domain(
    val id: String,
    val name: String,
    val hexColor: String,
    val iconKey: String, // Maps to a UI resource in the View layer
    val isActive: Boolean = true,
    val lastModified: Long
)

data class Topic(
    val id: String,
    val domainId: String,
    val name: String,
    val isActive: Boolean,
    val lastModified: Long
)

data class Space(
    val id: String,
    val name: String,
    val iconKey: String,
    val defaultBiophilia: Int?,
    val isControlled: Boolean,
    val isActive: Boolean,
    val lastModified: Long
)