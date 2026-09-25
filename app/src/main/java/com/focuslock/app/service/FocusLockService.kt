package com.focuslock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focuslock.app.MainActivity
import com.focuslock.app.R
import com.focuslock.app.data.permissions.PermissionChecker
import com.focuslock.app.data.usage.NewLock
import com.focuslock.app.data.usage.PollResult
import com.focuslock.app.data.usage.UsageTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Keeps usage tracking alive while FocusLock is in the background.
 *
 * A foreground service (type `specialUse`, with a low-priority notification) is what stops Android from
 * killing the process shortly after the user leaves the app. The work itself is a polling loop that
 * asks [UsageTracker] to read new usage events, update today's totals in Room and lock apps that have
 * used up their allowance (see [onLocked]; the accessibility service does the actual blocking).
 *
 * Polling is kept cheap on purpose: a few seconds apart while a limited app is on screen (so the
 * numbers feel live), slower otherwise, and almost paused while the screen is off; turning the
 * screen on wakes it immediately. While a limited app is on screen the loop also sleeps exactly until
 * that app would reach its limit.
 */
@AndroidEntryPoint
class FocusLockService : Service() {

    @Inject lateinit var tracker: UsageTracker
    @Inject lateinit var permissions: PermissionChecker
    @Inject lateinit var lockNotifier: LockNotifier
    @Inject lateinit var lockLauncher: LockLauncher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    /** Conflated: several wake-ups while a poll is running collapse into one extra poll. */
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wakeUp.trySend(Unit)
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.monitor_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.monitor_channel_description) },
        )
        // ACTION_SCREEN_ON can't be declared in the manifest, so it is registered while the service lives.
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within seconds of startForegroundService(), so before anything else.
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification) // the manifest's foregroundServiceType applies
        }

        // Called again every time the app opens; only one loop may ever run.
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_STICKY // if the system kills the process, it restarts the service
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenOnReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        val powerManager = getSystemService(PowerManager::class.java)
        while (scope.isActive) {
            if (!permissions.hasUsageAccess()) {
                // Nothing can be measured without it; the app restarts the service once it is granted again.
                Log.i(TAG, "Usage access missing; stopping")
                stopSelf()
                return
            }

            val screenOn = powerManager.isInteractive
            val result = try {
                tracker.poll(screenOn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One failed poll (database busy, system service hiccup) must not end tracking for good.
                Log.w(TAG, "Poll failed; will retry", e)
                PollResult.NOTHING
            }
            result.newlyLocked.forEach(::onLocked)

            var delayMs = when {
                !screenOn -> SCREEN_OFF_INTERVAL_MS
                result.trackedAppOnScreen -> ACTIVE_INTERVAL_MS
                else -> IDLE_INTERVAL_MS
            }
            // Wake right when the app on screen will hit its limit, so the lock lands on the second it is
            // reached instead of up to a whole interval later.
            result.msUntilNextLimit?.let { delayMs = minOf(delayMs, (it + LIMIT_WAKE_MARGIN_MS).coerceAtLeast(MIN_WAKE_MS)) }
            withTimeoutOrNull(delayMs) { wakeUp.receive() }
        }
    }

    /**
     * An app just used up its allowance. The flag is already saved (which is what the blocker reads); here
     * the user is told and, if the app is on screen right now, thrown out of it mid-use.
     */
    private fun onLocked(lock: NewLock) {
        Log.i(TAG, "Locked ${lock.app.packageName} (${lock.app.usedSeconds}s of ${lock.app.limitSeconds}s)")
        lockNotifier.notifyLocked(lock.app)
        if (lock.onScreen && !FocusLockAccessibilityService.enforceNow(lock.app.packageName)) {
            // Accessibility is off, so Home can't be pressed. Cover the app instead; the dashboard tells the
            // user that full blocking needs the permission.
            lockLauncher.showLockScreen(lock.app.packageName)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focuslock)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE) // no 10 s delay on Android 12+
            .build()
    }

    companion object {
        private const val TAG = "FocusLockService"
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 1

        // Poll cadence. The events carry exact timestamps, so a slower cadence never loses time; it only
        // delays how soon the totals reflect it. A lock does not wait for the cadence: see LIMIT_WAKE_MARGIN_MS.
        private const val ACTIVE_INTERVAL_MS = 3_000L
        private const val IDLE_INTERVAL_MS = 5_000L
        private const val SCREEN_OFF_INTERVAL_MS = 60_000L

        // The poll after "limit reached in X ms" runs a hair later, so the whole-second total has ticked over.
        private const val LIMIT_WAKE_MARGIN_MS = 50L
        private const val MIN_WAKE_MS = 100L

        /** Starts (or, if already running, nudges) the service. Call while the app is visible. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, FocusLockService::class.java))
            } catch (e: IllegalStateException) {
                // Android 12+ refuses foreground-service starts from the background. The app is opened by the
                // user whenever this is called, so it should not happen; if it does, the next launch retries.
                Log.w(TAG, "Could not start the service right now", e)
            }
        }
    }
}
