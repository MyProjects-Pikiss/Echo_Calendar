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

    @Query(
        "SELECT Event.* FROM Event " +
            "JOIN EventFts ON Event.id = EventFts.eventId " +
            "WHERE EventFts MATCH :query " +
            "ORDER BY Event.occurredAt DESC, Event.updatedAt DESC"
    )
    suspend fun fullTextSearch(query: String): List<EventEntity>
}
