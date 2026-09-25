package com.focuslock.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focuslock.app.MainActivity
import com.focuslock.app.R
import com.focuslock.app.data.block.LockedApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "App locked" and "App unlocked" notices. Tapping either opens that app's edit screen in FocusLock,
 * where "Unlock now" lives. Silently does nothing while notifications are switched off: the lock itself
 * never depends on it.
 */
@Singleton
class LockNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun notifyLocked(app: LockedApp) = post(
        id = lockId(app.packageName),
        packageName = app.packageName,
        title = context.getString(R.string.lock_notification_title, app.label),
        text = context.getString(R.string.lock_notification_text),
        silent = false,
    )

    /**
     * "Chrome unlocked · You have 15 min more today." Quiet, because the user has just asked for it. It has
     * its own id, so clearing the lock notice (which happens when the app leaves the locked set) can't
     * remove it, and a later re-lock replaces neither.
     */
    fun notifyUnlocked(packageName: String, label: String, minutes: Int) = post(
        id = unlockId(packageName),
        packageName = packageName,
        title = context.getString(R.string.unlock_notification_title, label),
        text = context.getString(R.string.unlock_notification_text, minutesText(minutes)),
        silent = true,
    )

    /** Removes the "locked" notice only; an "unlocked" notice stays until tapped or dismissed. */
    fun cancel(packageName: String) = NotificationManagerCompat.from(context).cancel(lockId(packageName))

    private fun post(id: Int, packageName: String, title: String, text: String, silent: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.lock_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.lock_channel_description) },
        )
        val open = PendingIntent.getActivity(
            context,
            id,
            MainActivity.editIntent(context, packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focuslock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(silent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check above and now.
            Log.w(TAG, "Notification permission missing", e)
        }
    }

    /** "15 min", "1 h" or "1 h 30 min", like the on-screen durations. */
    private fun minutesText(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> context.getString(R.string.duration_min, rest)
            rest == 0 -> context.getString(R.string.duration_h, hours)
            else -> context.getString(R.string.duration_h_min, hours, rest)
        }
    }

    // Both ranges are distinct from the service's notification id (1); one notice per app and kind.
    private fun lockId(packageName: String) = 1_000 + (packageName.hashCode() and 0xFFFFF)
    private fun unlockId(packageName: String) = 2_000_000 + (packageName.hashCode() and 0xFFFFF)

    private companion object {
        const val TAG = "LockNotifier"
        const val CHANNEL_ID = "locks"
    }
}
