package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventLabelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: EventLabelCrossRef)

    @Query(
        "SELECT Label.* FROM Label " +
            "INNER JOIN EventLabel ON Label.id = EventLabel.labelId " +
            "WHERE EventLabel.eventId = :eventId ORDER BY Label.name ASC"
    )
    suspend fun getLabelsForEvent(eventId: String): List<LabelEntity>

    @Query(
        "SELECT EventLabel.eventId FROM EventLabel " +
            "INNER JOIN Label ON Label.id = EventLabel.labelId " +
            "WHERE LOWER(Label.name) IN (:normalizedLabelNames) " +
            "GROUP BY EventLabel.eventId " +
            "HAVING COUNT(DISTINCT LOWER(Label.name)) = :requiredCount"
    )
    suspend fun getEventIdsContainingAllLabels(
        normalizedLabelNames: List<String>,
        requiredCount: Int
    ): List<String>

    @Query("DELETE FROM EventLabel WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)
}
