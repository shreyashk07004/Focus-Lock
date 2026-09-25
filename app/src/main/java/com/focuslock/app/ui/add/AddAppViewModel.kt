package com.focuslock.app.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.data.repository.AppLimitRepository
import com.focuslock.app.data.repository.DailyLimit
import com.focuslock.app.data.repository.InstalledApp
import com.focuslock.app.data.repository.InstalledAppsRepository
import com.focuslock.app.ui.common.LimitInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddAppUiState {
    data object Loading : AddAppUiState
    data object Error : AddAppUiState

    /**
     * @property apps the addable apps that match the search (already-added apps are excluded)
     * @property noAppsLeft true when nothing is addable at all, as opposed to nothing matching the search
     * @property selected the app whose limit is being set, or null while still choosing
     */
    data class Ready(
        val apps: List<InstalledApp>,
        val noAppsLeft: Boolean,
        val selected: InstalledApp?,
    ) : AddAppUiState
}

@HiltViewModel
class AddAppViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val appLimitRepository: AppLimitRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private sealed interface Load {
        data object Loading : Load
        data object Failed : Load
        data class Loaded(val apps: List<InstalledApp>) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)

    // Kept in SavedStateHandle so the search, the chosen app and the typed limit survive process
    // death. The text fields are bound to these StateFlows directly (not to a combined flow), so a
    // keystroke is reflected synchronously and the cursor never jumps.
    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
    val limitText: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_LIMIT, DailyLimit.DEFAULT_MINUTES.toString())
    private val selectedPackage = savedStateHandle.getStateFlow<String?>(KEY_SELECTED, null)

    val uiState: StateFlow<AddAppUiState> = combine(
        load,
        appLimitRepository.observeBlockedApps(),
        query,
        selectedPackage,
    ) { load, added, query, selected -> buildState(load, added.map { it.packageName }.toSet(), query, selected) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddAppUiState.Loading)

    private var saving = false
    private val savedEvents = Channel<Unit>(Channel.BUFFERED)

    /** Emits once, after the new rule is safely in Room. The screen leaves when it arrives. */
    val saved: Flow<Unit> = savedEvents.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        load.value = Load.Loading
        viewModelScope.launch {
            load.value = try {
                Load.Loaded(installedAppsRepository.getLaunchableApps())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed
            }
        }
    }

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    fun onSelect(app: InstalledApp) {
        savedStateHandle[KEY_LIMIT] = DailyLimit.DEFAULT_MINUTES.toString()
        savedStateHandle[KEY_SELECTED] = app.packageName
    }

    /** Back from the limit step to the list. */
    fun onDeselect() {
        savedStateHandle[KEY_SELECTED] = null
    }

    fun onLimitTextChange(text: String) {
        savedStateHandle[KEY_LIMIT] = text
    }

    fun save() {
        val app = (uiState.value as? AddAppUiState.Ready)?.selected ?: return
        val minutes = LimitInput.parse(limitText.value) ?: return
        if (saving) return // ignore a double tap
        saving = true
        viewModelScope.launch {
            try {
                appLimitRepository.add(app.packageName, app.label, minutes)
                savedEvents.send(Unit)
            } finally {
                saving = false
            }
        }
    }

    private fun buildState(load: Load, addedPackages: Set<String>, query: String, selected: String?): AddAppUiState =
        when (load) {
            Load.Loading -> AddAppUiState.Loading
            Load.Failed -> AddAppUiState.Error
            is Load.Loaded -> {
                val available = load.apps.filterNot { it.packageName in addedPackages }
                val needle = query.trim()
                AddAppUiState.Ready(
                    apps = if (needle.isEmpty()) available else available.filter { it.label.contains(needle, ignoreCase = true) },
                    noAppsLeft = available.isEmpty(),
                    // Resolved from the full list, not `available`: right after Save the app counts as
                    // "added", and the screen must not flash back to the list before it navigates away.
                    selected = load.apps.firstOrNull { it.packageName == selected },
                )
            }
        }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_SELECTED = "selected"
        const val KEY_LIMIT = "limit"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
