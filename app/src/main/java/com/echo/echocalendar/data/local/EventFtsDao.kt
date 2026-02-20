package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(eventFts: EventFtsEntity)

    @Query("DELETE FROM EventFts WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)
}
