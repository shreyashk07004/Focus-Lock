package com.focuslock.app.data.usage

import com.focuslock.app.data.db.BlockedApp
import com.focuslock.app.data.db.DailyUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRulesTest {

    private fun rule(limitMinutes: Int = 1, enabled: Boolean = true) =
        BlockedApp("com.android.chrome", "Chrome", limitMinutes, enabled, createdAt = 0)

    private fun day(used: Long = 0, locked: Boolean = false, bonus: Long = 0) =
        DailyUsage(packageName = "com.android.chrome", dateKey = "2026-09-22", usedSeconds = used, isLockedToday = locked, bonusSeconds = bonus)

    @Test
    fun `not locked below the limit`() {
        assertFalse(LockRules.isLocked(rule(1), day(used = 59)))
    }

    @Test
    fun `locked exactly at the limit`() {
        assertTrue(LockRules.isLocked(rule(1), day(used = 60)))
    }

    @Test
    fun `locked above the limit even if the flag is not written yet`() {
        assertTrue(LockRules.isLocked(rule(1), day(used = 75, locked = false)))
    }

    @Test
    fun `flag alone locks, even below the limit`() {
        // e.g. the rule was edited after the lock was recorded: the flag stays until unlock or a raised limit.
        assertTrue(LockRules.isLocked(rule(10), day(used = 30, locked = true)))
    }

    @Test
    fun `paused rule is never locked`() {
        assertFalse(LockRules.isLocked(rule(1, enabled = false), day(used = 500, locked = true)))
    }

    @Test
    fun `no usage row means not locked`() {
        assertFalse(LockRules.isLocked(rule(1), null))
    }

    @Test
    fun `bonus extends the limit`() {
        val app = rule(1)
        // 1 min limit + 15 min bonus: 5 min used is fine, 16 min is locked again.
        assertFalse(LockRules.isLocked(app, day(used = 300, bonus = 900)))
        assertTrue(LockRules.isLocked(app, day(used = 960, bonus = 900)))
        assertEquals(960, LockRules.effectiveLimitSeconds(app, day(bonus = 900)))
    }

    @Test
    fun `unlocked state right after Unlock now is stable`() {
        // Used 65 s of a 60 s limit, locked. Unlock adds 5 min and clears the flag: must not read as locked.
        assertTrue(LockRules.isLocked(rule(1), day(used = 65, locked = true)))
        assertFalse(LockRules.isLocked(rule(1), day(used = 65, locked = false, bonus = 300)))
    }

    @Test
    fun `unlock grants the full bonus when the app locked right at its limit`() {
        assertEquals(900L, LockRules.bonusToGrant(rule(1), day(used = 60, locked = true), bonusMinutes = 15))
    }

    @Test
    fun `unlock also covers time already used beyond the limit`() {
        // 30 min limit, 38 min used (limit was lowered afterwards): +5 min must mean 5 min from now.
        val app = rule(30)
        val today = day(used = 38 * 60L, locked = true)
        val grant = LockRules.bonusToGrant(app, today, bonusMinutes = 5)
        assertEquals(8 * 60L + 5 * 60L, grant)
        val afterUnlock = today.copy(isLockedToday = false, bonusSeconds = grant)
        assertFalse(LockRules.isLocked(app, afterUnlock))
        assertEquals(5 * 60_000L, LockRules.msUntilLimit(app, afterUnlock, usedMs = 38 * 60_000L))
    }

    @Test
    fun `second unlock stacks on the first`() {
        val app = rule(1)
        val afterFirst = day(used = 960, bonus = 900) // used the whole 1 + 15 min
        assertEquals(300L, LockRules.bonusToGrant(app, afterFirst, bonusMinutes = 5))
    }

    @Test
    fun `limit is editable until extra time is granted`() {
        assertTrue(LockRules.canEditLimit(null))
        assertTrue(LockRules.canEditLimit(day(used = 500, locked = true)))
        assertFalse(LockRules.hasExtraTime(day(used = 500)))

        val granted = day(used = 500, bonus = 300)
        assertTrue(LockRules.hasExtraTime(granted))
        assertFalse(LockRules.canEditLimit(granted))
    }

    @Test
    fun `limit stays read-only after the extra time is used up and the app re-locks`() {
        val relocked = day(used = 2_580, locked = true, bonus = 780)
        assertTrue(LockRules.isLocked(rule(30), relocked))
        assertFalse(LockRules.canEditLimit(relocked))
    }

    @Test
    fun `time until limit counts down from the effective limit`() {
        val app = rule(1)
        assertEquals(40_000L, LockRules.msUntilLimit(app, day(used = 20), usedMs = 20_000))
        assertEquals(340_000L, LockRules.msUntilLimit(app, day(used = 20, bonus = 300), usedMs = 20_000))
    }

    @Test
    fun `no countdown once locked`() {
        assertNull(LockRules.msUntilLimit(rule(1), day(used = 60), usedMs = 60_000))
        assertNull(LockRules.msUntilLimit(rule(1), day(used = 5, locked = true), usedMs = 5_000))
    }

    @Test
    fun `countdown works before any row exists`() {
        assertEquals(60_000L, LockRules.msUntilLimit(rule(1), null, usedMs = 0))
    }
}
