package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventAlarmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: EventAlarmEntity)

    @Query("SELECT * FROM EventAlarm WHERE eventId = :eventId")
    suspend fun getByEventId(eventId: String): List<EventAlarmEntity>

    @Query("DELETE FROM EventAlarm WHERE id = :id")
    suspend fun delete(id: String)
}
