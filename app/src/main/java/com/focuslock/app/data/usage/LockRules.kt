package com.focuslock.app.data.usage

import com.focuslock.app.data.db.BlockedApp
import com.focuslock.app.data.db.DailyUsage

/**
 * The one definition of "is this app locked right now?", shared by the tracker, the blocker, the lock
 * screen and the dashboard so that they can never disagree. Pure Kotlin, so it is unit tested on the JVM.
 */
object LockRules {

    /** The rule's daily limit plus whatever bonus "Unlock now" granted today. */
    fun effectiveLimitSeconds(limitMinutes: Int, bonusSeconds: Long): Long = limitMinutes * 60L + bonusSeconds

    fun effectiveLimitSeconds(app: BlockedApp, today: DailyUsage?): Long =
        effectiveLimitSeconds(app.dailyLimitMinutes, today?.bonusSeconds ?: 0L)

    /**
     * Locked when the rule is on and either the day's row says so, or the used time has reached the limit.
     * The second condition closes the gap between reaching the limit and the tracker writing the flag.
     * A paused rule is never locked.
     */
    fun isLocked(app: BlockedApp, today: DailyUsage?): Boolean {
        if (!app.isEnabled || today == null) return false
        return today.isLockedToday || today.usedSeconds >= effectiveLimitSeconds(app, today)
    }

    /** Extra time was granted today (the day's allowance is limit + bonus, not just the limit). */
    fun hasExtraTime(today: DailyUsage?): Boolean = (today?.bonusSeconds ?: 0L) > 0L

    /**
     * The daily limit can only be changed while no extra time is active. The bonus was worked out against
     * the limit as it was at unlock time, so changing the limit afterwards would double-count or re-lock at
     * once. It applies for the rest of the day and resets with the date.
     */
    fun canEditLimit(today: DailyUsage?): Boolean = !hasExtraTime(today)

    /**
     * Bonus seconds "Unlock now" must add so that [bonusMinutes] of use remain *from now*. If the app is
     * already past its limit (say the limit was lowered after the time was used), a plain "+N min" would
     * still leave it over the limit and it would lock again at once, so the overshoot is added as well.
     */
    fun bonusToGrant(app: BlockedApp, today: DailyUsage?, bonusMinutes: Int): Long {
        val overshoot = ((today?.usedSeconds ?: 0L) - effectiveLimitSeconds(app, today)).coerceAtLeast(0L)
        return overshoot + bonusMinutes * 60L
    }

    /**
     * Milliseconds until [app] reaches its limit if it stays on screen, given [usedMs] so far; null when
     * it is already locked. The service uses it to wake up exactly when the limit is crossed.
     */
    fun msUntilLimit(app: BlockedApp, today: DailyUsage?, usedMs: Long): Long? {
        if (isLocked(app, today)) return null
        return (effectiveLimitSeconds(app, today) * 1000L - usedMs).coerceAtLeast(0L)
    }
}
