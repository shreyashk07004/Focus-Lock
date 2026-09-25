package com.focuslock.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lists apps the user can pick from. "Installed" here means *launchable*: apps with a launcher
 * entry. That is what `<queries>` in the manifest makes visible, and it excludes background-only
 * system packages that nobody can open (and therefore nobody can meaningfully limit).
 */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        // FocusLock must never block itself, and blocking the home screen would trap the user.
        val excluded = buildSet {
            add(context.packageName)
            pm.queryActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                .forEach { add(it.activityInfo.packageName) }
        }

        pm.queryActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            .asSequence()
            .map { it.activityInfo.packageName to it }
            .filter { (packageName, _) -> packageName !in excluded }
            .distinctBy { (packageName, _) -> packageName } // apps can expose several launcher activities
            .map { (packageName, info) ->
                InstalledApp(
                    packageName = packageName,
                    label = info.loadLabel(pm).toString().ifBlank { packageName },
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    private fun PackageManager.queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            queryIntentActivities(intent, 0)
        }
}
