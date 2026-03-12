package com.mochame.app.domain.model

/**
 * The "Artist" - Represents the creator of the wisdom (Author).
 */
data class Author(
    val id: String, // UUID for sync-readiness
    val name: String,
    val lastModified: Long // Required for 2027 conflict resolution
)

/**
 * Represents a book or collection of wisdom.
 */
data class Book(
    val id: String, // UUID
    val authorId: String, // FK to Author
    val title: String,
    val dateAdded: Long,
    val dateFinished: Long?, // Nullable if the book is still in progress
    val isActive: Boolean,
    val lastModified: Long // Explicit sync timestamp
)

/**
 * The specific quote injected into the user's day.
 */
data class Quote(
    val id: String, // UUID
    val bookId: String, // FK to Book
    val content: String,
    val resonance: Resonance, // Domain enum for semantic "Blend" serving
    val viewCount: Int, // Key for the "Infinite Loop" selection logic
    val lastModified: Long
)

/**
 * The "Flavor" - Categorization for targeted wisdom delivery.
 */
enum class Resonance {
    WONDER,
    LOGIC,
    SADNESS,
    JOY
}