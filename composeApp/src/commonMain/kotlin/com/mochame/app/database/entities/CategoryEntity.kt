package com.mochame.app.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hexColor: String,
    val isActive: Boolean = true,
    val lastModified: Long
)