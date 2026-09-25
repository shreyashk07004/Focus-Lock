package com.focuslock.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The single on-device database. The schema is exported to `app/schemas/` on every build so
 * future migrations can be checked against it. Bump [version] and add a Migration on any change.
 */
@Database(
    entities = [BlockedApp::class, DailyUsage::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        const val NAME = "focuslock.db"

        /** Phase 5: "Unlock now" grants bonus seconds for the day, stored on the day's usage row. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_usage ADD COLUMN bonusSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** The minutes the user chose in "Unlock now", kept apart from the bonus used in the maths. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_usage ADD COLUMN grantedSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
