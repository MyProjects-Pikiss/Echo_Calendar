package com.echo.echocalendar.domain.usecase

import androidx.room.withTransaction
import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.EventEntity
import com.echo.echocalendar.data.local.EventLabelCrossRef
import java.util.UUID

class SaveEventUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(
        categoryId: String,
        occurredAt: Long,
        summary: String,
        body: String,
        placeText: String?,
        labels: List<String>
    ): String {
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val event = EventEntity(
            id = eventId,
            categoryId = categoryId,
            occurredAt = occurredAt,
            summary = summary,
            placeText = placeText,
            body = body,
            createdAt = now,
            updatedAt = now
        )

        database.withTransaction {
            database.eventDao().upsert(event)
            val labelDao = database.labelDao()
            val eventLabelDao = database.eventLabelDao()
            labels
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .forEach { labelName ->
                    val label = labelDao.getOrCreate(labelName, now)
                    eventLabelDao.insert(
                        EventLabelCrossRef(
                            eventId = eventId,
                            labelId = label.id
                        )
                    )
                }
        }

        return eventId
    }
}
