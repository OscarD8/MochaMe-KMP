package com.mochame.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.app.domain.signal.Resonance

@Entity(tableName = "authors")
data class AuthorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lastModified: Long
)

@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = AuthorEntity::class,
            parentColumns = ["id"],
            childColumns = ["authorId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["authorId"])] // Optimized for Author -> Book lookups
)
data class BookEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val title: String,
    val dateAdded: Long,
    val dateFinished: Long?,
    val isActive: Boolean,
    val lastModified: Long
)

@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("bookId"),
        Index("resonance"),
        Index("viewCount"),
        Index("lastModified")
    ]
)
data class QuoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val content: String,
    val resonance: Resonance, // Handled by TypeConverter
    val viewCount: Int = 0,
    val lastModified: Long
)