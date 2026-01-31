package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.Fts5
import androidx.room.ColumnInfo

@Fts5
@Entity(tableName = "EventFts")
data class EventFtsEntity(
    @ColumnInfo(name = "eventId") val eventId: String,
    val summary: String,
    val body: String,
    val placeText: String?
)
