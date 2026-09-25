package com.focuslock.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {

    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey")
    fun observeForDate(dateKey: String): Flow<List<DailyUsage>>

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND dateKey = :dateKey")
    suspend fun get(packageName: String, dateKey: String): DailyUsage?

    /**
     * Inserts or replaces the row for (packageName, dateKey). `@Upsert` is deliberately not used:
     * it only resolves primary-key conflicts, and callers don't know the auto-generated `id`, so a
     * clash on the unique (packageName, dateKey) index would throw. REPLACE handles that index.
     * The row's `id` may change on replace; nothing references it.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: DailyUsage)

    /** Creates the (packageName, dateKey) row if it does not exist yet; an existing row is left alone. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(usage: DailyUsage): Long

    /**
     * Sets only the used time. The usage tracker writes through this (after [insertIfAbsent]) instead of
     * [upsert], so it can never overwrite `isLockedToday`, which other code changes independently.
     * (A single-statement SQL upsert would be neater, but needs SQLite 3.24 and minSdk 26 ships older.)
     */
    @Query("UPDATE daily_usage SET usedSeconds = :usedSeconds WHERE packageName = :packageName AND dateKey = :dateKey")
    suspend fun setUsedSeconds(packageName: String, dateKey: String, usedSeconds: Long)

    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey")
    suspend fun getForDate(dateKey: String): List<DailyUsage>

    /**
     * Locks the app if (and only if) it is not locked yet and its used time has reached the limit plus
     * today's bonus. Returns 1 exactly once per crossing, so the caller knows this is the moment to
     * react, and because the check and the write are one statement it cannot race with an unlock.
     */
    @Query(
        "UPDATE daily_usage SET isLockedToday = 1 " +
            "WHERE packageName = :packageName AND dateKey = :dateKey AND isLockedToday = 0 " +
            "AND usedSeconds >= :limitSeconds + bonusSeconds",
    )
    suspend fun lockIfOverLimit(packageName: String, dateKey: String, limitSeconds: Long): Int

    /**
     * "Unlock now": adds [bonusSeconds] to today's allowance (what the limit maths uses) and [grantedSeconds]
     * to the running total the user chose (what the screens show), and clears the lock.
     */
    @Query(
        "UPDATE daily_usage SET bonusSeconds = bonusSeconds + :bonusSeconds, " +
            "grantedSeconds = grantedSeconds + :grantedSeconds, isLockedToday = 0 " +
            "WHERE packageName = :packageName AND dateKey = :dateKey",
    )
    suspend fun addBonusAndUnlock(packageName: String, dateKey: String, bonusSeconds: Long, grantedSeconds: Long)

    @Query("UPDATE daily_usage SET isLockedToday = 0 WHERE packageName = :packageName AND dateKey = :dateKey")
    suspend fun clearLock(packageName: String, dateKey: String)

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName")
    suspend fun getForPackage(packageName: String): List<DailyUsage>

    /** Puts back rows captured before a delete, so "Undo" restores usage history too. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usage: List<DailyUsage>)

    @Query("DELETE FROM daily_usage WHERE packageName = :packageName")
    suspend fun deleteForPackage(packageName: String)

    /** Drops history older than [dateKey]; `yyyy-MM-dd` keys sort chronologically as text. */
    @Query("DELETE FROM daily_usage WHERE dateKey < :dateKey")
    suspend fun deleteBefore(dateKey: String)
}
