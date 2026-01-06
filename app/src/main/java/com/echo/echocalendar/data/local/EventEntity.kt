package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Event",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["categoryId", "occurredAt"],
            orders = [Index.Order.ASC, Index.Order.DESC]
        ),
        Index(value = ["occurredAt"], orders = [Index.Order.DESC])
    ]
)
data class EventEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val occurredAt: Long,
    val summary: String,
    val placeText: String?,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
)
