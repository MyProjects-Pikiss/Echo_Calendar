package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LabelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: LabelEntity): Long

    @Query("SELECT * FROM Label WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): LabelEntity?

    @Transaction
    suspend fun getOrCreate(name: String, createdAt: Long): LabelEntity {
        val existing = getByName(name)
        if (existing != null) return existing

        val id = insert(LabelEntity(name = name, createdAt = createdAt))
        if (id != -1L) {
            return LabelEntity(id = id, name = name, createdAt = createdAt)
        }
        return requireNotNull(getByName(name))
    }

    @Query("SELECT * FROM Label ORDER BY name ASC")
    suspend fun getAll(): List<LabelEntity>
}
