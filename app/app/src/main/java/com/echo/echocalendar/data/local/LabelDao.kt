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

    @Query("SELECT * FROM Label WHERE id = :labelId LIMIT 1")
    suspend fun getById(labelId: Long): LabelEntity?

    @Transaction
    suspend fun getOrCreate(name: String, createdAt: Long, source: String = LABEL_SOURCE_USER): LabelEntity {
        val existing = getByName(name)
        if (existing != null) return existing

        val id = insert(LabelEntity(name = name, createdAt = createdAt, source = source))
        if (id != -1L) {
            return LabelEntity(id = id, name = name, createdAt = createdAt, source = source)
        }
        return requireNotNull(getByName(name))
    }

    @Query("SELECT * FROM Label ORDER BY name ASC")
    suspend fun getAll(): List<LabelEntity>

    @Query(
        "SELECT Label.id AS id, Label.name AS name, Label.createdAt AS createdAt, Label.source AS source, " +
            "COUNT(EventLabel.eventId) AS eventCount FROM Label " +
            "LEFT JOIN EventLabel ON Label.id = EventLabel.labelId " +
            "GROUP BY Label.id, Label.name, Label.createdAt, Label.source " +
            "ORDER BY Label.name ASC"
    )
    suspend fun getAllWithEventCounts(): List<LabelWithEventCount>

    @Query(
        "SELECT Event.* FROM Event " +
            "INNER JOIN EventLabel ON Event.id = EventLabel.eventId " +
            "WHERE EventLabel.labelId = :labelId " +
            "ORDER BY Event.occurredAt DESC, Event.updatedAt DESC"
    )
    suspend fun getEventsByLabelId(labelId: Long): List<EventEntity>

    @Query("UPDATE Label SET name = :name WHERE id = :labelId")
    suspend fun renameById(labelId: Long, name: String)

    @Query("DELETE FROM Label WHERE id = :labelId")
    suspend fun deleteById(labelId: Long)

    @Query(
        "INSERT OR IGNORE INTO EventLabel(eventId, labelId) " +
            "SELECT eventId, :targetLabelId FROM EventLabel WHERE labelId = :sourceLabelId"
    )
    suspend fun copyEventLinks(sourceLabelId: Long, targetLabelId: Long)

    @Transaction
    suspend fun addLabel(name: String, createdAt: Long): Boolean {
        if (getByName(name) != null) return false
        return insert(LabelEntity(name = name, createdAt = createdAt, source = LABEL_SOURCE_USER)) != -1L
    }

    @Transaction
    suspend fun renameLabel(labelId: Long, name: String): Boolean {
        val existing = getByName(name)
        if (existing != null && existing.id != labelId) return false
        if (getById(labelId) == null) return false
        renameById(labelId, name)
        return true
    }

    @Transaction
    suspend fun mergeLabels(sourceLabelId: Long, targetLabelId: Long): Boolean {
        if (sourceLabelId == targetLabelId) return false
        if (getById(sourceLabelId) == null || getById(targetLabelId) == null) return false
        copyEventLinks(sourceLabelId = sourceLabelId, targetLabelId = targetLabelId)
        deleteById(sourceLabelId)
        return true
    }
}
