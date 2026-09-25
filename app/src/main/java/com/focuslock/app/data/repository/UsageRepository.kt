package com.focuslock.app.data.repository

import com.focuslock.app.data.db.BlockedAppDao
import com.focuslock.app.data.db.DailyUsage
import com.focuslock.app.data.db.DailyUsageDao
import com.focuslock.app.data.usage.LockRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Per-day usage and lock state. The usage tracker writes it; the UI and the blocker read it. */
@Singleton
class UsageRepository @Inject constructor(
    private val dailyUsageDao: DailyUsageDao,
    private val blockedAppDao: BlockedAppDao,
) {
    /** One consistent view of "today": its key and the instant it began. */
    data class Day(val key: String, val startMs: Long)

    /**
     * Today in local time. Read once and use both fields, so a call straddling midnight can't mix two days.
     * Phase 6 will make this respect the user's configurable reset hour.
     */
    fun today(): Day {
        val date = LocalDate.now()
        return Day(date.toString(), date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    /** Today's date key in local time (`yyyy-MM-dd`). */
    fun todayKey(): String = today().key

    /** Emits today's key now and again each time the local date changes. */
    private fun todayKeyFlow(): Flow<String> = flow {
        while (true) {
            emit(todayKey())
            val tomorrow = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            // +1 s so the wake-up lands safely after midnight even with a little timer drift.
            delay(tomorrow.toEpochMilli() - System.currentTimeMillis() + 1_000)
        }
    }.distinctUntilChanged()

    /** Usage rows for today; keeps following "today" if the screen stays open past midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(): Flow<List<DailyUsage>> =
        todayKeyFlow().flatMapLatest { dailyUsageDao.observeForDate(it) }

    /** When today ends and the counters and locks start over: the next local midnight. */
    fun nextResetMs(): Long =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    suspend fun get(packageName: String, dateKey: String = todayKey()): DailyUsage? =
        dailyUsageDao.get(packageName, dateKey)

    /** Every usage row of [dateKey] (default: today) in one query. */
    suspend fun getForDate(dateKey: String = todayKey()): List<DailyUsage> = dailyUsageDao.getForDate(dateKey)

    /** Follows one app's row for today, or null while it has none yet. */
    fun observeTodayFor(packageName: String): Flow<DailyUsage?> =
        observeToday().map { rows -> rows.firstOrNull { it.packageName == packageName } }.distinctUntilChanged()

    /**
     * Locks [packageName] for [dateKey] if its used time has reached [limitSeconds] (plus today's bonus).
     * True only for the call that actually flipped the lock, i.e. the moment the limit was crossed.
     */
    suspend fun lockIfOverLimit(packageName: String, dateKey: String, limitSeconds: Long): Boolean =
        dailyUsageDao.lockIfOverLimit(packageName, dateKey, limitSeconds) > 0

    /**
     * Emergency unlock: gives [bonusMinutes] more for today, counted from now, and lifts today's lock. The
     * bonus is needed because the used time is at or above the limit; without it the tracker would lock the
     * app again on its next poll.
     */
    suspend fun unlock(packageName: String, bonusMinutes: Int) {
        val key = todayKey()
        dailyUsageDao.insertIfAbsent(DailyUsage(packageName = packageName, dateKey = key))
        val app = blockedAppDao.get(packageName) ?: return
        val grant = LockRules.bonusToGrant(app, dailyUsageDao.get(packageName, key), bonusMinutes)
        dailyUsageDao.addBonusAndUnlock(packageName, key, bonusSeconds = grant, grantedSeconds = bonusMinutes * 60L)
    }

    suspend fun clearLock(packageName: String, dateKey: String = todayKey()) =
        dailyUsageDao.clearLock(packageName, dateKey)

    suspend fun save(usage: DailyUsage) = dailyUsageDao.upsert(usage)

    /** Records how long [packageName] has been in the foreground on [dateKey], creating the row if needed. */
    suspend fun recordUsedSeconds(packageName: String, dateKey: String, usedSeconds: Long) {
        dailyUsageDao.insertIfAbsent(DailyUsage(packageName = packageName, dateKey = dateKey))
        dailyUsageDao.setUsedSeconds(packageName, dateKey, usedSeconds)
    }
}
