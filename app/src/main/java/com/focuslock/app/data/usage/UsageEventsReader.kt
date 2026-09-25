package com.focuslock.app.data.usage

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the system's usage-event log through `UsageStatsManager`. Requires the Usage Access grant;
 * without it the system silently returns an empty log (or throws SecurityException on some devices).
 *
 * Events are used rather than `queryUsageStats(INTERVAL_DAILY)`: the daily buckets are coarse, can
 * overlap the previous day, and are only refreshed occasionally, while events carry exact timestamps.
 */
@Singleton
class UsageEventsReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val usageStats: UsageStatsManager
        get() = context.getSystemService(UsageStatsManager::class.java)

    /** Relevant events in `[fromMs, toMs)`, oldest first. Everything else in the log is skipped. */
    // ACTIVITY_* / DEVICE_SHUTDOWN are API 29+ names, but their values are the same as the old
    // MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND, so the inlined constants are right on API 26 too.
    @SuppressLint("InlinedApi")
    fun read(fromMs: Long, toMs: Long): List<ForegroundEvent> {
        val log = usageStats.queryEvents(fromMs, toMs) ?: return emptyList()
        val result = ArrayList<ForegroundEvent>()
        val event = UsageEvents.Event() // reused: getNextEvent fills it in place
        while (log.hasNextEvent()) {
            log.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEventKind.RESUMED
                // A stop always follows a pause, but on some devices only one of them is logged.
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundEventKind.PAUSED

                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN -> ForegroundEventKind.EVERYTHING_PAUSED

                else -> continue
            }
            result += ForegroundEvent(
                kind = kind,
                packageName = event.packageName.orEmpty(),
                className = event.className.orEmpty(),
                timeMs = event.timeStamp,
            )
        }
        return result
    }
}
