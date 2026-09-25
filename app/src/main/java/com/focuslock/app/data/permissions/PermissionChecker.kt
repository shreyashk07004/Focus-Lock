package com.focuslock.app.data.permissions

import android.app.AppOpsManager
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.focuslock.app.service.FocusLockAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which of the four grants FocusLock asks for are currently in place. */
data class PermissionStatus(
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val notifications: Boolean = false,
) {
    /**
     * Enough to track usage and show progress. Overlay and accessibility are only needed to *block* an app
     * (Phase 5), so they do not gate tracking.
     */
    val trackingReady: Boolean get() = usageAccess && notifications

    val allGranted: Boolean get() = usageAccess && overlay && accessibility && notifications
}

/**
 * Reads the current grants and publishes them as [status].
 *
 * None of these grants can be observed, so the state is only as fresh as the last [refresh].
 * `MainActivity` calls it on every resume, which is when the user comes back from a Settings page.
 */
@Singleton
class PermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _status = MutableStateFlow(read())
    val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    fun refresh() {
        _status.value = read()
    }

    /** Cheap, direct check (an AppOps binder call) for code that must not rely on the cached [status]. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            // No explicit choice recorded: fall back to the manifest permission's own state.
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun read() = PermissionStatus(
        usageAccess = hasUsageAccess(),
        overlay = Settings.canDrawOverlays(context),
        accessibility = isAccessibilityServiceEnabled(),
        // Covers both the API 33+ runtime permission and the user switching notifications off in Settings.
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    )

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabled.isNullOrEmpty()) return false
        val ours = ComponentName(context, FocusLockAccessibilityService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == ours) return true
        }
        return false
    }
}
