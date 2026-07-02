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
                "USING fts4(eventId, summary, body, placeText, tokenize=unicode61)"
        )
        db.execSQL(
            "INSERT INTO EventFts(eventId, summary, body, placeText) " +
                "SELECT id, summary, body, placeText FROM Event"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `EventAlarm` (" +
                "`id` TEXT NOT NULL, " +
                "`eventId` TEXT NOT NULL, " +
                "`triggerAt` INTEGER NOT NULL, " +
                "`isEnabled` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`eventId`) REFERENCES `Event`(`id`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_EventAlarm_eventId` " +
                "ON `EventAlarm` (`eventId`)"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `EventRawInput` (" +
                "`eventId` TEXT NOT NULL, " +
                "`rawText` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`eventId`), " +
                "FOREIGN KEY(`eventId`) REFERENCES `Event`(`id`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_EventRawInput_eventId` " +
                "ON `EventRawInput` (`eventId`)"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `Event` ADD COLUMN `isYearlyRecurring` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `Label` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'user'"
        )
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `LabelFilterPreset` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`includeMode` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_LabelFilterPreset_name` " +
                "ON `LabelFilterPreset` (`name`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `LabelFilterPresetRule` (" +
                "`presetId` INTEGER NOT NULL, " +
                "`labelId` INTEGER NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "PRIMARY KEY(`presetId`, `labelId`, `role`), " +
                "FOREIGN KEY(`presetId`) REFERENCES `LabelFilterPreset`(`id`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE, " +
                "FOREIGN KEY(`labelId`) REFERENCES `Label`(`id`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_LabelFilterPresetRule_presetId` " +
                "ON `LabelFilterPresetRule` (`presetId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_LabelFilterPresetRule_labelId` " +
                "ON `LabelFilterPresetRule` (`labelId`)"
        )
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
