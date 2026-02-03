package com.echo.echocalendar.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS EventFts")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS EventFts " +
                "USING fts4(summary, body, placeText, content='Event')"
        )
        db.execSQL("INSERT INTO EventFts(EventFts) VALUES('rebuild')")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS EventFts")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS EventFts " +
                "USING fts4(summary, body, placeText, content='Event')"
        )
        db.execSQL("INSERT INTO EventFts(EventFts) VALUES('rebuild')")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS EventFts")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS EventFts " +
                "USING fts4(eventId, summary, body, placeText)"
        )
        db.execSQL(
            "INSERT INTO EventFts(eventId, summary, body, placeText) " +
                "SELECT id, summary, body, placeText FROM Event"
        )
    }
}
