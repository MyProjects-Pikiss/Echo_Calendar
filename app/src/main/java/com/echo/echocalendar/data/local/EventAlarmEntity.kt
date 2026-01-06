package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "EventAlarm",
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
data class EventAlarmEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val triggerAt: Long,
    val isEnabled: Boolean
)
