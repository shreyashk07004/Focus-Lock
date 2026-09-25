package com.focuslock.app.data.usage

import com.focuslock.app.data.block.LockedApp
import com.focuslock.app.data.db.DailyUsage
import com.focuslock.app.data.repository.AppLimitRepository
import com.focuslock.app.data.repository.UsageRepository
import javax.inject.Inject

/** An app whose limit was crossed during a poll, and whether it is on screen at this moment. */
data class NewLock(val app: LockedApp, val onScreen: Boolean)

/** What one [UsageTracker.poll] found; the service decides from it what to do and when to poll next. */
data class PollResult(
    /** A rule-limited app is on screen with the screen on (the service polls faster then). */
    val trackedAppOnScreen: Boolean,
    /** Apps that reached their limit during this poll, i.e. the moment to lock them. */
    val newlyLocked: List<NewLock>,
    /** How long until the soonest on-screen app hits its limit, so the service can wake exactly then. */
    val msUntilNextLimit: Long?,
) {
    companion object {
        val NOTHING = PollResult(trackedAppOnScreen = false, newlyLocked = emptyList(), msUntilNextLimit = null)
    }
}

/**
 * One polling step of the usage tracking, owned by `FocusLockService` (one instance per service).
 *
 * It keeps a running [ForegroundTimeAccumulator] for *every* app and only asks the system for events
 * newer than the last poll, so each poll is cheap. When the service starts (or restarts after being
 * killed) the first poll reads the whole day again, so no time is lost while it was not running.
 * Only apps with an enabled rule are written to `daily_usage`; the rest are just kept in memory, which
 * is what lets a newly added rule show today's earlier usage straight away.
 *
 * It is also where a lock is decided: when an enabled app's used time reaches its limit (plus today's
 * bonus) the day's row is flagged `isLockedToday` and the app is reported in [PollResult.newlyLocked].
 *
 * Not thread-safe: call [poll] from one coroutine at a time.
 */
class UsageTracker @Inject constructor(
    private val reader: UsageEventsReader,
    private val appLimits: AppLimitRepository,
    private val usage: UsageRepository,
) {
    private var day: UsageRepository.Day? = null
    private var accumulator = ForegroundTimeAccumulator(0)

    /** Where the next event query starts: just after the newest event handled so far. */
    private var cursorMs = 0L

    /** The last instant the screen was seen on; a session still open while the screen is off counts up to here. */
    private var liveUntilMs = 0L

    /** Seconds last written per package, so an unchanged app costs no database write. */
    private val lastWritten = HashMap<String, Long>()

    private companion object {
        /** Long enough to see the start of any realistic session that spans midnight (a screen-off ends them all). */
        const val LOOK_BACK_MS = 12 * 60 * 60 * 1000L
    }

    /**
     * Reads new events, writes today's used seconds of every enabled rule whose value changed, and locks
     * the rules that have used up their allowance. [screenOn] is passed in so this class stays free of
     * Android services.
     */
    suspend fun poll(screenOn: Boolean): PollResult {
        val now = System.currentTimeMillis()
        val today = usage.today()
        val current = day
        when {
            current == null -> {
                // First poll of this service run: rebuild the whole day from the event log. The read starts a
                // little *before* midnight so an app that was already on screen at midnight shows its resume;
                // the accumulator counts that session only from midnight on.
                accumulator = ForegroundTimeAccumulator(today.startMs)
                cursorMs = today.startMs - LOOK_BACK_MS
                lastWritten.clear()
            }

            current.key != today.key -> {
                // Midnight passed while running. An app still on screen carries over into the new day.
                accumulator.startNewWindow(today.startMs)
                lastWritten.clear()
            }
        }
        day = today

        for (event in reader.read(cursorMs, now)) {
            accumulator.onEvent(event)
            cursorMs = maxOf(cursorMs, event.timeMs + 1)
        }
        if (screenOn) liveUntilMs = now

        val enabled = appLimits.getEnabled()
        // Forget removed/paused rules so that if one comes back its row is written again.
        lastWritten.keys.retainAll(enabled.mapTo(HashSet()) { it.packageName })

        // Bonus and lock flag of today's rows, read once per poll.
        val rows = usage.getForDate(today.key).associateBy { it.packageName }

        var trackedAppOnScreen = false
        var msUntilNextLimit: Long? = null
        val newlyLocked = ArrayList<NewLock>()
        for (app in enabled) {
            val totalMs = accumulator.totalMs(app.packageName, liveUntilMs)
            val seconds = totalMs / 1000
            if (lastWritten[app.packageName] != seconds) {
                usage.recordUsedSeconds(app.packageName, today.key, seconds)
                lastWritten[app.packageName] = seconds
            }
            val onScreen = accumulator.isInForeground(app.packageName)
            val row = (rows[app.packageName] ?: DailyUsage(packageName = app.packageName, dateKey = today.key))
                .copy(usedSeconds = seconds)

            // The database does the "reached the limit, not locked yet" test itself, in one statement, so a
            // concurrent unlock can never be overwritten. It says yes exactly once per crossing.
            val baseLimitSeconds = app.dailyLimitMinutes * 60L
            if (seconds >= baseLimitSeconds && !row.isLockedToday &&
                usage.lockIfOverLimit(app.packageName, today.key, baseLimitSeconds)
            ) {
                newlyLocked += NewLock(
                    app = LockedApp(
                        packageName = app.packageName,
                        label = app.appLabel,
                        usedSeconds = seconds,
                        limitSeconds = LockRules.effectiveLimitSeconds(app, row),
                    ),
                    onScreen = onScreen && screenOn,
                )
            }

            if (onScreen && screenOn) {
                trackedAppOnScreen = true
                LockRules.msUntilLimit(app, row, totalMs)?.let { ms ->
                    msUntilNextLimit = minOf(msUntilNextLimit ?: ms, ms)
                }
            }
        }
        return PollResult(trackedAppOnScreen, newlyLocked, msUntilNextLimit)
    }
}
