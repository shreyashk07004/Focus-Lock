package com.focuslock.app.data.repository

import androidx.room.withTransaction
import com.focuslock.app.data.db.AppDatabase
import com.focuslock.app.data.db.BlockedApp
import com.focuslock.app.data.db.BlockedAppDao
import com.focuslock.app.data.db.DailyUsageDao
import com.focuslock.app.data.usage.LockRules
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Source of truth for which apps have a daily limit. Hides Room from the UI. */
@Singleton
class AppLimitRepository @Inject constructor(
    private val database: AppDatabase,
    private val blockedAppDao: BlockedAppDao,
    private val dailyUsageDao: DailyUsageDao,
    private val usage: UsageRepository,
) {
    fun observeBlockedApps(): Flow<List<BlockedApp>> = blockedAppDao.observeAll()

    fun observe(packageName: String): Flow<BlockedApp?> = blockedAppDao.observe(packageName)

    /** Rules that are switched on, i.e. the apps whose usage is tracked. */
    suspend fun getEnabled(): List<BlockedApp> = blockedAppDao.getEnabled()

    suspend fun get(packageName: String): BlockedApp? = blockedAppDao.get(packageName)

    /** Inserts a new rule or overwrites the existing one for the same package. */
    suspend fun save(app: BlockedApp) = blockedAppDao.upsert(app)

    /** Creates an enabled rule for [packageName]; the limit is clamped to [DailyLimit]'s bounds. */
    suspend fun add(packageName: String, label: String, dailyLimitMinutes: Int) = blockedAppDao.upsert(
        BlockedApp(
            packageName = packageName,
            appLabel = label,
            dailyLimitMinutes = DailyLimit.coerce(dailyLimitMinutes),
            isEnabled = true,
            createdAt = System.currentTimeMillis(),
        ),
    )

    suspend fun setEnabled(packageName: String, enabled: Boolean) =
        blockedAppDao.setEnabled(packageName, enabled)

    /**
     * Changes the limit and returns whether it did. Refused while extra time is active today (see
     * [LockRules.canEditLimit]); the screen greys the editor out, this keeps the rule true for any caller.
     * If the app is locked today and the new limit leaves room again, the lock is lifted: raising the
     * limit is one of the two ways back in (the other is "Unlock now").
     */
    suspend fun setLimit(packageName: String, minutes: Int): Boolean = database.withTransaction {
        val dateKey = usage.todayKey()
        val today = dailyUsageDao.get(packageName, dateKey)
        if (!LockRules.canEditLimit(today)) return@withTransaction false

        val limit = DailyLimit.coerce(minutes)
        blockedAppDao.setLimit(packageName, limit)
        if (today != null && today.isLockedToday && today.usedSeconds < LockRules.effectiveLimitSeconds(limit, today.bonusSeconds)) {
            dailyUsageDao.clearLock(packageName, dateKey)
        }
        true
    }

    /**
     * Deletes the rule and its usage history atomically so no orphaned rows are left behind.
     * Returns what was removed (for [restore]), or null if there was no such rule.
     */
    suspend fun remove(packageName: String): RemovedRule? = database.withTransaction {
        val app = blockedAppDao.get(packageName) ?: return@withTransaction null
        val usage = dailyUsageDao.getForPackage(packageName)
        blockedAppDao.delete(packageName)
        dailyUsageDao.deleteForPackage(packageName)
        RemovedRule(app, usage)
    }

    /** Undoes [remove]: puts the rule and its usage rows back in one transaction. */
    suspend fun restore(rule: RemovedRule) = database.withTransaction {
        blockedAppDao.upsert(rule.app)
        dailyUsageDao.insertAll(rule.usage)
    }
}
