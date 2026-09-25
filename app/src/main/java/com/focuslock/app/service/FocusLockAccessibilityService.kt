package com.focuslock.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focuslock.app.data.block.BlockPolicy
import com.focuslock.app.data.block.LockedApp
import com.focuslock.app.data.block.ProtectedApps
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where a locked app is actually stopped.
 *
 * Android cannot kill another app, so the block works like StayFree and AppBlock: the system tells this
 * service whenever a different window comes to the front (`TYPE_WINDOW_STATE_CHANGED`, instant, unlike
 * polling usage stats), and if that window belongs to a locked app the service presses Home and puts the
 * lock screen up. Reopening the app raises the same event, so it is bounced again.
 *
 * The set of locked apps is kept in memory (fed by [BlockPolicy]), so reacting to a window change is a map
 * lookup with no database access. It never reads what is on screen (`canRetrieveWindowContent` is off).
 */
@AndroidEntryPoint
class FocusLockAccessibilityService : AccessibilityService() {

    @Inject lateinit var policy: BlockPolicy
    @Inject lateinit var launcher: LockLauncher
    @Inject lateinit var notifier: LockNotifier
    @Inject lateinit var protectedApps: ProtectedApps

    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null

    /** Latest locked apps. Written by the collector, read on the main thread for every event. */
    @Volatile private var locked: Map<String, LockedApp> = emptyMap()

    /** The last regular app window seen (not the status bar or the keyboard). Main thread only. */
    private var lastAppPackage: String? = null

    /** When each package was last enforced (elapsedRealtime), to swallow the burst of events one launch causes. */
    private val lastEnforcedAt = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope ->
            scope.launch {
                try {
                    policy.lockedApps().collect { onLockedChanged(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Blocking would silently stop, so at least say so. The service reconnects on the next enable.
                    Log.e(TAG, "Lost the locked-apps feed", e)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The notification shade and the keyboard open windows on top of whatever app is showing; they
        // say nothing about which app is in front.
        if (pkg == SYSTEM_UI || event.className?.startsWith(IME_WINDOW_PREFIX) == true) return

        lastAppPackage = pkg
        if (protectedApps.isProtected(pkg)) return
        if (locked.containsKey(pkg)) enforce(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        if (instance === this) instance = null
        scope?.cancel()
        scope = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun onLockedChanged(now: Map<String, LockedApp>) {
        val before = locked
        locked = now
        // Whatever left the set (unlocked, paused, removed) no longer needs its "locked" notice.
        for (pkg in before.keys - now.keys) notifier.cancel(pkg)
    }

    /**
     * Sends the user home, then shows the lock screen. Home goes first: it is a key event the system
     * handles a moment later, so a lock screen started at the same instant could be hidden by it. Main thread only.
     */
    private fun enforce(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        val last = lastEnforcedAt[pkg]
        if (last != null && now - last < DEBOUNCE_MS) return
        lastEnforcedAt[pkg] = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed(
            {
                // Unlocked in the meantime? Then there is nothing to show.
                if (locked.containsKey(pkg)) launcher.showLockScreen(pkg)
            },
            LOCK_LAUNCH_DELAY_MS,
        )
    }

    /** The usage tracker saw [pkg] cross its limit while on screen. Main thread only. */
    private fun enforceIfForeground(pkg: String) {
        // The tracker's view can lag a moment; if another regular app is in front by now, leave it alone.
        val front = lastAppPackage
        if (front != null && front != pkg) return
        enforce(pkg)
    }

    companion object {
        private const val TAG = "FocusLockA11y"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val IME_WINDOW_PREFIX = "android.inputmethodservice"
        private const val DEBOUNCE_MS = 800L

        /** Gap between Home and the lock screen. Long enough for Home to win, short enough to feel instant. */
        private const val LOCK_LAUNCH_DELAY_MS = 250L

        @Volatile
        private var instance: FocusLockAccessibilityService? = null

        /**
         * Lets the usage service lock an app the instant its limit is crossed, without waiting for the
         * next window change. False if the accessibility service is not running (permission off), in
         * which case the caller falls back to showing the lock screen alone.
         */
        fun enforceNow(packageName: String): Boolean {
            val service = instance ?: return false
            service.handler.post { service.enforceIfForeground(packageName) }
            return true
        }
    }
}
