package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val LABEL_FILTER_INCLUDE_MODE_ALL = "all"
const val LABEL_FILTER_INCLUDE_MODE_ANY = "any"

@Entity(
    tableName = "LabelFilterPreset",
    indices = [Index(value = ["name"], unique = true)]
)
data class LabelFilterPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val includeMode: String = LABEL_FILTER_INCLUDE_MODE_ANY,
    val createdAt: Long,
    val updatedAt: Long
)
