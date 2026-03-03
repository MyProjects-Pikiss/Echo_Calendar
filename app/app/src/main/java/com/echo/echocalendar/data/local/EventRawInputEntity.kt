package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "EventRawInput",
    indices = [
        Index(value = ["eventId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class EventRawInputEntity(
    @PrimaryKey val eventId: String,
    val rawText: String,
    val updatedAt: Long
)
