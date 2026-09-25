package com.focuslock.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps ORDER BY appLabel COLLATE NOCASE")
    fun observeAll(): Flow<List<BlockedApp>>

    /** The rules that are currently being enforced (paused rules are not tracked). */
    @Query("SELECT * FROM blocked_apps WHERE isEnabled = 1")
    suspend fun getEnabled(): List<BlockedApp>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName")
    fun observe(packageName: String): Flow<BlockedApp?>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): BlockedApp?

    @Upsert
    suspend fun upsert(app: BlockedApp)

    // Field-level updates (rather than read-modify-write of the whole row) so that toggling a rule
    // and editing its limit can never overwrite each other with stale data.
    @Query("UPDATE blocked_apps SET isEnabled = :isEnabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, isEnabled: Boolean)

    @Query("UPDATE blocked_apps SET dailyLimitMinutes = :minutes WHERE packageName = :packageName")
    suspend fun setLimit(packageName: String, minutes: Int)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
