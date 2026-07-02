package com.echo.echocalendar.data.local

data class LabelFilterPresetSummary(
    val preset: LabelFilterPresetEntity,
    val includeLabels: List<LabelWithEventCount>,
    val excludeLabels: List<LabelWithEventCount>
)
