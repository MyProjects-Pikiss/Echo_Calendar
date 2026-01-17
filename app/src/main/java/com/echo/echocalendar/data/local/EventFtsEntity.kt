package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "EventFts")
data class EventFtsEntity(
    val eventId: String,
    val summary: String,
    val body: String,
    val placeText: String?
)
