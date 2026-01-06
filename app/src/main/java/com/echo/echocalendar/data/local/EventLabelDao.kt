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
}
