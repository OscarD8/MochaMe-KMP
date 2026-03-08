package com.mochame.app.domain.model

/**
 * A specific moment of logging.
 */
data class MomentDraft(
    val domainId: String,
    val topicId: String?,
    val spaceId: String?,
    val core: MomentCore,
    val detail: MomentDetail
)

data class Moment(
    val id: String,
    val domainId: String,
    val topicId: String?,
    val spaceId: String?,
    val core: MomentCore,
    val detail: MomentDetail,
    val context: MomentClimate,
    val metadata: MomentMetadata
)

// 1. The Core (Required User Input)
data class MomentCore(
    val satisfactionScore: Int,
    val moodScore: Int,
    val energyDelta: Int,
    val intensityScale: Int,
)

// 2. The subjective "Extra" - Optional from User
data class MomentDetail(
    val note: String? = null,
    val isFocusTime: Boolean? = null,
    val socialScale: Int? = null,
    val entryEnergy: Int? = null,
    val biophiliaScale: Int? = null,
    val durationMinutes: Int? = null
)

// 3. Automated by System
data class MomentClimate(
    val isDaylight: Boolean? = null,
    val cloudDensity: Int? = null,
    val isPrecipitating: Boolean? = null
)

// 4. The system "Audit" - Managed by Repository
data class MomentMetadata(
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