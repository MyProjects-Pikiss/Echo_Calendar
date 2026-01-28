package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.Fts5
import androidx.room.PrimaryKey

@Fts5
@Entity(tableName = "EventFts", primaryKeys = ["eventId"])
data class EventFtsEntity(
    @PrimaryKey val eventId: String,
    val summary: String,
    val body: String,
    val placeText: String?
)
