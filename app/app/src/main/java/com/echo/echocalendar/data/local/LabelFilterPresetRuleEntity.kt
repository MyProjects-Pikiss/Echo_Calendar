package com.echo.echocalendar.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

const val LABEL_FILTER_RULE_EXCLUDE = "exclude"
const val LABEL_FILTER_RULE_INCLUDE = "include"

@Entity(
    tableName = "LabelFilterPresetRule",
    primaryKeys = ["presetId", "labelId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = LabelFilterPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
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
    indices = [
        Index(value = ["presetId"]),
        Index(value = ["labelId"])
    ]
)
data class LabelFilterPresetRuleEntity(
    val presetId: Long,
    val labelId: Long,
    val role: String
)
