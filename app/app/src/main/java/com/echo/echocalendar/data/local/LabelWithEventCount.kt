package com.echo.echocalendar.data.local

data class LabelWithEventCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val source: String,
    val eventCount: Int
)
