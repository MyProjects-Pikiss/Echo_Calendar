package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ColumnInfo
import androidx.room.FtsOptions

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "EventFts")
data class EventFtsEntity(
    @ColumnInfo(name = "eventId") val eventId: String,
    val summary: String,
    val body: String,
    val placeText: String?
)
