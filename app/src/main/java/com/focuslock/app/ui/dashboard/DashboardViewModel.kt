package com.focuslock.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.data.db.BlockedApp
import com.focuslock.app.data.db.DailyUsage
import com.focuslock.app.data.permissions.PermissionChecker
import com.focuslock.app.data.repository.AppLimitRepository
import com.focuslock.app.data.repository.RemovedRule
import com.focuslock.app.data.repository.UsageRepository
import com.focuslock.app.data.usage.LockRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A rule together with today's usage row (null until something was recorded). */
data class AppUsageItem(val app: BlockedApp, val today: DailyUsage? = null) {
    val usedSeconds: Long get() = today?.usedSeconds ?: 0L

    /** The limit including any bonus "Unlock now" granted today. */
    val limitSeconds: Long get() = LockRules.effectiveLimitSeconds(app, today)
    val remainingSeconds: Long get() = (limitSeconds - usedSeconds).coerceAtLeast(0)

    /** Extra time granted today, as the user chose it in "Unlock now". */
    val extraSeconds: Long get() = today?.grantedSeconds ?: 0L
    val hasExtra: Boolean get() = LockRules.hasExtraTime(today)

    /** Same test the blocker uses, so the card says "Locked" exactly when the app really is locked. */
    val isLocked: Boolean get() = LockRules.isLocked(app, today)

    /** 0..1 for the progress bar. */
    val fraction: Float get() = (usedSeconds.toFloat() / limitSeconds).coerceIn(0f, 1f)
}

/** The grants without which nothing can be tracked; anything still missing blocks the usage display. */
enum class EssentialPermission { USAGE_ACCESS, NOTIFICATIONS }

/** The grants that only matter for *locking* an app; without them usage is still counted. */
enum class BlockingPermission { ACCESSIBILITY, OVERLAY }

sealed interface DashboardUiState {
    /** Before Room's first emission; avoids flashing the empty state at start-up. */
    data object Loading : DashboardUiState

    /**
     * [missingSetup] lists the essential grants still missing. While it is non-empty the cards show
     * only the limit and no progress, because no usage is being recorded.
     */
    data class Content(
        val apps: List<AppUsageItem>,
        val missingSetup: List<EssentialPermission> = emptyList(),
        /** Set only while an enabled rule exists, since without one there is nothing to lock. */
        val missingBlocking: List<BlockingPermission> = emptyList(),
    ) : DashboardUiState {
        val trackingReady: Boolean get() = missingSetup.isEmpty()
    }
}

/**
 * Owns the list plus every delete on it, whether it started as a swipe here or as the delete
 * button on the edit screen, so that both end in the same Undo window.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AppLimitRepository,
    usageRepository: UsageRepository,
    permissions: PermissionChecker,
) : ViewModel() {

    // Three live sources in one state: the rules, today's usage rows (written by the tracking service)
    // and the permission status. Any of them changing redraws the cards.
    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeBlockedApps(),
        usageRepository.observeToday(),
        permissions.status,
    ) { apps, usage, granted ->
        val todayByPackage = usage.associateBy { it.packageName }
        DashboardUiState.Content(
            apps = apps.map { AppUsageItem(it, todayByPackage[it.packageName]) },
            missingSetup = buildList {
                if (!granted.usageAccess) add(EssentialPermission.USAGE_ACCESS)
                if (!granted.notifications) add(EssentialPermission.NOTIFICATIONS)
            },
            missingBlocking = buildList {
                if (apps.any { it.isEnabled }) {
                    if (!granted.accessibility) add(BlockingPermission.ACCESSIBILITY)
                    if (!granted.overlay) add(BlockingPermission.OVERLAY)
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardUiState.Loading)

    /** A swipe that is waiting for the user to confirm in the dialog. */
    private val _deleteRequest = MutableStateFlow<BlockedApp?>(null)
    val deleteRequest: StateFlow<BlockedApp?> = _deleteRequest.asStateFlow()

    /** The most recent delete that can still be undone. Drives the snackbar. */
    private val _undo = MutableStateFlow<RemovedRule?>(null)
    val undo: StateFlow<RemovedRule?> = _undo.asStateFlow()

    private var undoExpiry: Job? = null

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(packageName, enabled) }
    }

    fun requestDelete(app: BlockedApp) {
        _deleteRequest.value = app
    }

    fun cancelDelete() {
        _deleteRequest.value = null
    }

    fun confirmDelete() {
        val app = _deleteRequest.value ?: return
        _deleteRequest.value = null
        remove(app.packageName)
    }

    fun undoDelete() {
        val rule = _undo.value ?: return
        undoExpiry?.cancel()
        _undo.value = null
        viewModelScope.launch { repository.restore(rule) }
    }

    /**
     * Removes the rule and opens the Undo window. Called after the swipe dialog is confirmed, and by
     * the nav host when the edit screen (which confirms with its own dialog) asks for a removal.
     */
    fun remove(packageName: String) {
        viewModelScope.launch {
            val removed = repository.remove(packageName) ?: return@launch
            undoExpiry?.cancel()
            _undo.value = removed
            // The undo offer lives here (not in the snackbar's own timer) so it keeps counting
            // down while the user is on another screen and is never shown long after the fact.
            undoExpiry = launch {
                delay(UNDO_WINDOW_MS)
                _undo.value = null
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val UNDO_WINDOW_MS = 10_000L
    }
}
