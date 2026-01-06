package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "EventLabel",
    primaryKeys = ["eventId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["labelId"])]
)
data class EventLabelCrossRef(
    val eventId: String,
    val labelId: Long
)
