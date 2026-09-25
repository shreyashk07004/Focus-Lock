package com.focuslock.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.focuslock.app.ui.lock.LockActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the lock screen and the home screen from wherever the blocker happens to run (a service, so no
 * Activity context). Starting an activity from the background is only allowed with "Display over other
 * apps"; when the system refuses, nothing throws, and the user is still bounced home by the accessibility
 * service, so a failure here degrades to "sent home without an explanation" instead of a crash.
 */
@Singleton
class LockLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun showLockScreen(packageName: String) {
        try {
            context.startActivity(LockActivity.intent(context, packageName))
        } catch (e: Exception) {
            Log.w(TAG, "Could not show the lock screen for $packageName", e)
        }
    }

    fun goHome() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not go to the home screen", e)
        }
    }

    private companion object {
        const val TAG = "LockLauncher"
    }
}
