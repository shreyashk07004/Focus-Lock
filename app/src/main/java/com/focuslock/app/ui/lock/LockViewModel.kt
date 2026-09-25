package com.focuslock.app.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.data.repository.AppLimitRepository
import com.focuslock.app.data.repository.UsageRepository
import com.focuslock.app.data.usage.LockRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface LockUiState {
    /** Before the first database answer; the screen shows nothing rather than flash the wrong state. */
    data object Loading : LockUiState

    data class Locked(
        val packageName: String,
        val label: String,
        val usedSeconds: Long,
        /** The limit including today's bonus. */
        val limitSeconds: Long,
    ) : LockUiState

    /** Not locked (any more): unlocked in FocusLock, rule paused, rule removed, or a stale request. */
    data object NotLocked : LockUiState
}

/**
 * Follows one app's rule and today's usage, so the lock screen always shows the real numbers and closes
 * itself the moment the app stops being locked (which is what makes "Unlock now" and pausing a rule
 * take effect immediately, even while this screen is showing).
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    appLimits: AppLimitRepository,
    private val usage: UsageRepository,
) : ViewModel() {

    private val packageName = MutableStateFlow<String?>(null)

    /** Points the screen at another app (the activity is reused when a second app gets locked). */
    fun show(packageName: String?) {
        this.packageName.value = packageName
    }

    /** Midnight, as an epoch time. Kept here so the UI does not reach into repositories. */
    fun nextResetMs(): Long = usage.nextResetMs()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LockUiState> = packageName
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(LockUiState.NotLocked)
            } else {
                combine(appLimits.observe(pkg), usage.observeTodayFor(pkg)) { app, today ->
                    when {
                        app == null || !LockRules.isLocked(app, today) -> LockUiState.NotLocked
                        else -> LockUiState.Locked(
                            packageName = pkg,
                            label = app.appLabel,
                            usedSeconds = today?.usedSeconds ?: 0L,
                            limitSeconds = LockRules.effectiveLimitSeconds(app, today),
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
