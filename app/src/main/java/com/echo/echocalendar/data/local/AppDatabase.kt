package com.echo.echocalendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        EventEntity::class,
        LabelEntity::class,
        EventLabelCrossRef::class,
        EventFtsEntity::class,
        EventAlarmEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun eventDao(): EventDao
    abstract fun eventFtsDao(): EventFtsDao
    abstract fun labelDao(): LabelDao
    abstract fun eventLabelDao(): EventLabelDao
    abstract fun eventAlarmDao(): EventAlarmDao
}
