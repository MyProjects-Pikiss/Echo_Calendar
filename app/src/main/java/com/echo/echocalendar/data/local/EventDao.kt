package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Transaction
    suspend fun upsert(event: EventEntity) {
        CategoryDefaults.requireValidCategoryId(event.categoryId)
        insert(event)
    }

    @Transaction
    suspend fun insertAll(events: List<EventEntity>) {
        events.forEach { upsert(it) }
    }

    @Query(
        "SELECT * FROM Event WHERE categoryId = :categoryId ORDER BY occurredAt DESC, updatedAt DESC"
    )
    suspend fun getByCategory(categoryId: String): List<EventEntity>

    @Query("SELECT * FROM Event ORDER BY occurredAt DESC, updatedAt DESC")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM Event WHERE id = :eventId")
    suspend fun getById(eventId: String): EventEntity?

    @Query(
        "DELETE FROM Event WHERE summary LIKE :summaryPrefix " +
            "OR summary IN (:summaries)"
    )
    suspend fun deleteSeededEvents(summaryPrefix: String, summaries: List<String>)

    @Query(
        "SELECT Event.* FROM Event " +
            "JOIN EventFts ON Event.rowid = EventFts.rowid " +
            "WHERE EventFts MATCH :query " +
            "ORDER BY Event.occurredAt DESC, Event.updatedAt DESC"
    )
    suspend fun fullTextSearch(query: String): List<EventEntity>

    @Query(
        "SELECT * FROM Event WHERE occurredAt BETWEEN :start AND :end " +
            "ORDER BY occurredAt DESC, updatedAt DESC"
    )
    suspend fun getByOccurredAtRange(start: Long, end: Long): List<EventEntity>

    @Query("DELETE FROM Event WHERE id = :eventId")
    suspend fun deleteById(eventId: String)
}
