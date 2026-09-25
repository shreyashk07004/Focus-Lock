package com.focuslock.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.focuslock.app.data.permissions.PermissionChecker
import com.focuslock.app.service.FocusLockService
import com.focuslock.app.ui.nav.FocusLockNavHost
import com.focuslock.app.ui.nav.Routes
import com.focuslock.app.ui.theme.FocusLockTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The single activity of the app; all screens are Compose destinations hosted here. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissions: PermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Track usage as soon as Usage Access is granted. Runs each time the app becomes visible (and
        // whenever the grant flips), which also revives the service if the system has killed it. Starting
        // it here is allowed because the app is on screen; a background start would not be (Android 12+).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissions.status
                    .map { it.usageAccess }
                    .distinctUntilChanged()
                    .collect { granted -> if (granted) FocusLockService.start(this@MainActivity) }
            }
        }

        setContent {
            FocusLockTheme {
                FocusLockNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // None of the grants can be observed, so look again every time the user comes back to the app
        // (typically from a Settings page). Every screen and the service start above react to this.
        permissions.refresh()
    }

    companion object {
        /**
         * Opens FocusLock on the edit screen of [packageName], where a locked app can be unlocked. Used by
         * the lock screen's button and the "App locked" notification. The activity is `singleTask`, so an
         * already running instance receives it through `onNewIntent`, which the nav host handles.
         */
        fun editIntent(context: Context, packageName: String): Intent =
            Intent(Intent.ACTION_VIEW, Routes.editUri(packageName), context, MainActivity::class.java)
    }
}
