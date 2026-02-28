package com.echo.echocalendar.domain.usecase

import androidx.room.withTransaction
import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.EventEntity
import com.echo.echocalendar.data.local.EventFtsEntity
import com.echo.echocalendar.data.local.EventLabelCrossRef

class UpdateEventUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(
        eventId: String,
        categoryId: String,
        occurredAt: Long,
        summary: String,
        body: String,
        placeText: String?,
        labels: List<String>
    ) {
        val now = System.currentTimeMillis()
        val existing = database.eventDao().getById(eventId)
            ?: return
        val updatedEvent = EventEntity(
            id = eventId,
            categoryId = categoryId,
            occurredAt = occurredAt,
            summary = summary,
            placeText = placeText,
            body = body,
            createdAt = existing.createdAt,
            updatedAt = now
        )

        database.withTransaction {
            database.eventDao().upsert(updatedEvent)
            database.eventFtsDao().deleteByEventId(eventId)
            database.eventFtsDao().upsert(
                EventFtsEntity(
                    eventId = eventId,
                    summary = summary,
                    body = body,
                    placeText = placeText
                )
            )
            val labelDao = database.labelDao()
            val eventLabelDao = database.eventLabelDao()
            eventLabelDao.deleteByEventId(eventId)
            val existingLabelNames = labelDao.getAll().map { it.name }
            val resolvedLabels = harmonizeEventLabels(
                rawLabels = labels,
                existingLabelNames = existingLabelNames
            )
            resolvedLabels
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
    }
}
