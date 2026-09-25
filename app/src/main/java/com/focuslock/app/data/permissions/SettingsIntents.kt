package com.focuslock.app.data.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** The Settings page for each special grant. Each one lands on the exact screen, not the Settings home. */
object SettingsIntents {

    /** Usage access. The package URI jumps straight to FocusLock's own switch where the device supports it. */
    fun usageAccess(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun overlay(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** Android offers no deep link to a single accessibility service, so this opens the accessibility list. */
    fun accessibility(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Used for notifications when the runtime dialog can no longer be shown (or before Android 13). */
    fun appNotifications(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /**
     * Opens [intent]; if this device has no such page (some vendor builds drop the per-app variants),
     * falls back to the app's own details page, which always exists and has a Permissions entry.
     */
    fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(appDetails(context))
        }
    }
}
