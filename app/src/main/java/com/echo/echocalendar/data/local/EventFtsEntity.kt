package com.echo.echocalendar.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts5

@Fts5(contentEntity = EventEntity::class)
@Entity(tableName = "EventFts")
data class EventFtsEntity(
    @ColumnInfo(name = "id") val eventId: String,
    val summary: String,
    val body: String,
    val placeText: String?
)
