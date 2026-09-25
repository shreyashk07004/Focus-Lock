package com.focuslock.app.data.block

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Packages that must never be blocked: FocusLock itself (so the user can always change a limit) and every
 * home screen (blocking it would leave nowhere to go). The add-app screen already hides these, but a rule
 * could still exist for one, for example after the user installed a different launcher, so the blocker
 * checks again before it acts.
 */
@Singleton
class ProtectedApps @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var launchers: Set<String> = emptySet()
    private var launchersLoadedAt = Long.MIN_VALUE

    fun isProtected(packageName: String): Boolean =
        packageName == context.packageName || packageName in launcherPackages()

    /** Cached for a minute: this runs on window changes, and the default launcher rarely changes. */
    @Synchronized
    private fun launcherPackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (launchersLoadedAt == Long.MIN_VALUE || now - launchersLoadedAt > CACHE_MS) {
            launchers = queryLaunchers()
            launchersLoadedAt = now
        }
        return launchers
    }

    private fun queryLaunchers(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(home, 0)
        }
        return resolved.mapTo(HashSet()) { it.activityInfo.packageName }
    }

    private companion object {
        const val CACHE_MS = 60_000L
    }
}
