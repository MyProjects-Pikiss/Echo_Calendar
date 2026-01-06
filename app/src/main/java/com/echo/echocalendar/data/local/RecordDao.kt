package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Query("SELECT * FROM records WHERE text LIKE '%' || :query || '%' ORDER BY occurredAt DESC, createdAt DESC")
    suspend fun searchByText(query: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE occurredAt = :dateIso ORDER BY createdAt DESC")
    suspend fun getByDate(dateIso: String): List<RecordEntity>
}
