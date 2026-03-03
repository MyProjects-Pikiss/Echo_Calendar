package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventRawInputDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EventRawInputEntity)

    @Query("SELECT * FROM EventRawInput WHERE eventId = :eventId LIMIT 1")
    suspend fun getByEventId(eventId: String): EventRawInputEntity?

    @Query("SELECT * FROM EventRawInput WHERE eventId IN (:eventIds)")
    suspend fun getByEventIds(eventIds: List<String>): List<EventRawInputEntity>
}
