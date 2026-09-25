package com.focuslock.app.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.focuslock.app.MainActivity
import com.focuslock.app.service.LockLauncher
import com.focuslock.app.ui.theme.FocusLockTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen "Locked for today" page, shown when a locked app is opened (or locks while in use).
 *
 * It has exactly one way forward, "Manage in FocusLock", and no dismiss button: getting back into the app
 * means unlocking it inside FocusLock. Back goes to the home screen rather than to the blocked app below,
 * and the page closes itself as soon as the app stops being locked or the user leaves it.
 *
 * Declared `singleInstance` in its own task and excluded from recents (see the manifest), so it never
 * piles up on top of the blocked app's task and can't be resumed later from the app switcher.
 */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    @Inject lateinit var launcher: LockLauncher
    private val viewModel: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.show(intent.getStringExtra(EXTRA_PACKAGE))

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = leaveToHome()
            },
        )

        // Unlocked, paused or removed while the screen is up (or it was launched for an app that isn't
        // locked): there is nothing to show, so get out of the way.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { if (it is LockUiState.NotLocked) finish() }
            }
        }

        setContent {
            FocusLockTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LockScreen(
                    state = state,
                    resetAtMs = viewModel.nextResetMs(),
                    onManage = ::manageInFocusLock,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A second app got locked while this page was already up.
        viewModel.show(intent.getStringExtra(EXTRA_PACKAGE))
    }

    override fun onStop() {
        super.onStop()
        // Home, Recents or another app took over: this page has done its job. Rotation must not close it.
        if (!isChangingConfigurations) finish()
    }

    private fun manageInFocusLock() {
        val pkg = (viewModel.uiState.value as? LockUiState.Locked)?.packageName
            ?: intent.getStringExtra(EXTRA_PACKAGE)
        startActivity(
            if (pkg != null) MainActivity.editIntent(this, pkg) else Intent(this, MainActivity::class.java),
        )
        finish()
    }

    private fun leaveToHome() {
        launcher.goHome()
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "com.focuslock.app.extra.LOCKED_PACKAGE"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                // The blocker starts this from a service. No animation, so the app is covered at once.
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
    }
}
