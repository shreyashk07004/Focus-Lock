package com.focuslock.app.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.data.db.BlockedApp
import com.focuslock.app.data.db.DailyUsage
import com.focuslock.app.data.repository.AppLimitRepository
import com.focuslock.app.data.repository.UsageRepository
import com.focuslock.app.data.usage.LockRules
import com.focuslock.app.service.LockNotifier
import com.focuslock.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EditAppUiState {
    data object Loading : EditAppUiState
    data object NotFound : EditAppUiState

    /** [today] is this app's usage row for today (null before any usage was recorded). */
    data class Content(val app: BlockedApp, val today: DailyUsage? = null) : EditAppUiState {
        val isLocked: Boolean get() = LockRules.isLocked(app, today)
        val usedSeconds: Long get() = today?.usedSeconds ?: 0L

        /** Extra time granted today, as the user chose it (not the maths value, which may include overshoot). */
        val extraSeconds: Long get() = today?.grantedSeconds ?: 0L
        val hasExtra: Boolean get() = LockRules.hasExtraTime(today)
        val canEditLimit: Boolean get() = LockRules.canEditLimit(today)

        /** Live time left today, counting any extra. */
        val remainingSeconds: Long get() = (limitSeconds - usedSeconds).coerceAtLeast(0L)

        /** The limit including any bonus already granted today. */
        val limitSeconds: Long get() = LockRules.effectiveLimitSeconds(app, today)
    }
}

/**
 * Every change is written to Room the moment it is made; there is no Save button and no draft
 * to lose. Deleting is not done here: the screen hands the request to the dashboard, which owns
 * the undo snackbar (see FocusLockNavHost).
 */
@HiltViewModel
class EditAppViewModel @Inject constructor(
    private val repository: AppLimitRepository,
    private val usage: UsageRepository,
    private val lockNotifier: LockNotifier,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle[Routes.ARG_PACKAGE])

    val uiState: StateFlow<EditAppUiState> = flow {
        val first = repository.get(packageName)
        if (first == null) {
            emit(EditAppUiState.NotFound)
        } else {
            emit(EditAppUiState.Content(first, usage.get(packageName)))
            // A vanished row (null) is deliberately ignored: while the screen animates away after a
            // delete it keeps showing its last content instead of flashing a "not found" state.
            emitAll(
                combine(repository.observe(packageName).filterNotNull(), usage.observeTodayFor(packageName)) { app, today ->
                    EditAppUiState.Content(app, today)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EditAppUiState.Loading)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(packageName, enabled) }
    }

    fun setLimit(minutes: Int) {
        viewModelScope.launch { repository.setLimit(packageName, minutes) }
    }

    /**
     * Emergency unlock: [bonusMinutes] more for today, and the lock is lifted at once. Blocking stops
     * right away because the blocker watches the same database rows.
     */
    fun unlock(bonusMinutes: Int) {
        viewModelScope.launch {
            val app = repository.get(packageName) ?: return@launch
            usage.unlock(packageName, bonusMinutes)
            lockNotifier.cancel(packageName)
            // Exact by construction: after an unlock the time left equals the minutes chosen.
            lockNotifier.notifyUnlocked(packageName, app.appLabel, bonusMinutes)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
