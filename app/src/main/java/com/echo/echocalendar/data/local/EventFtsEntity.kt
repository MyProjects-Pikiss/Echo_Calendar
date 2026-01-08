package com.echo.echocalendar.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(contentEntity = EventEntity::class)
@Entity(tableName = "EventFts")
data class EventFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val summary: String,
    val body: String,
    val placeText: String?
)
