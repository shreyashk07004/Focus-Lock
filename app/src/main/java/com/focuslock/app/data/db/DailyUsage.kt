package com.focuslock.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Foreground time and lock state of one app on one calendar day. */
@Entity(
    tableName = "daily_usage",
    indices = [Index(value = ["packageName", "dateKey"], unique = true)],
)
data class DailyUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    /** Local date as `yyyy-MM-dd`. */
    val dateKey: String,
    val usedSeconds: Long = 0,
    val isLockedToday: Boolean = false,
    /**
     * Extra allowance granted for this day only (by "Unlock now"). It lives on the day's row, so it
     * disappears by itself when the date changes. The effective limit is the rule's limit plus this.
     */
    @ColumnInfo(defaultValue = "0") val bonusSeconds: Long = 0,
    /**
     * What the user actually asked for with "Unlock now", summed over the day. Display only: unlike
     * [bonusSeconds] it does not include time already used beyond the limit, so "+15 min" reads as +15.
     */
    @ColumnInfo(defaultValue = "0") val grantedSeconds: Long = 0,
)
