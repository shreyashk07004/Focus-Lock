package com.focuslock.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-chosen app together with its daily time limit. One row per package. */
@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    /** Cached display name so lists render without querying PackageManager. */
    val appLabel: String,
    val dailyLimitMinutes: Int,
    /** Lets the user pause a rule without deleting it. */
    val isEnabled: Boolean = true,
    val createdAt: Long,
)
