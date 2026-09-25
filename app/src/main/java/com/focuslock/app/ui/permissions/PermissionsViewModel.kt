package com.focuslock.app.ui.permissions

import androidx.lifecycle.ViewModel
import com.focuslock.app.data.permissions.PermissionChecker
import com.focuslock.app.data.permissions.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin window onto [PermissionChecker]. The status is re-read whenever the app resumes (in
 * `MainActivity`), so rows flip to green by themselves after the user returns from a Settings page.
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val checker: PermissionChecker,
) : ViewModel() {

    val status: StateFlow<PermissionStatus> = checker.status

    /** For results that arrive without the activity resuming, such as the notification dialog. */
    fun refresh() = checker.refresh()
}
