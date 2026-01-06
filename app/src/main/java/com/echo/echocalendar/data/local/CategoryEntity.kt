package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val sortOrder: Int
)
