package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface EventFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(eventFts: EventFtsEntity)
}
