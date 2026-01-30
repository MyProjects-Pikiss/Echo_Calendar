package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.Fts5

@Fts5
@Entity(tableName = "EventFts")
data class EventFtsEntity(
    @ColumnInfo(name = "eventId") val eventId: String,
    val summary: String,
    val body: String,
    val placeText: String?
)
