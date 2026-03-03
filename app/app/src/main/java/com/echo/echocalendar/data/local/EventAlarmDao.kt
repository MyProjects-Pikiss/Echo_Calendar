package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventAlarmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: EventAlarmEntity)

    @Query("SELECT * FROM EventAlarm WHERE eventId = :eventId LIMIT 1")
    suspend fun getByEventId(eventId: String): EventAlarmEntity?

    @Query("SELECT * FROM EventAlarm WHERE eventId IN (:eventIds)")
    suspend fun getByEventIds(eventIds: List<String>): List<EventAlarmEntity>

    @Query("SELECT * FROM EventAlarm WHERE isEnabled = 1")
    suspend fun getEnabledAlarms(): List<EventAlarmEntity>

    @Query("DELETE FROM EventAlarm WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM EventAlarm WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)
}
