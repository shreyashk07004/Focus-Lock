package com.focuslock.app.ui.nav

import android.net.Uri

object Routes {
    const val DASHBOARD = "dashboard"
    const val ADD = "add"
    const val PERMISSIONS = "permissions"

    const val ARG_PACKAGE = "packageName"
    const val EDIT = "edit/{$ARG_PACKAGE}"

    /**
     * Result key: the edit screen writes a package name under it into the dashboard's back-stack entry
     * (`entry.savedStateHandle`), and the nav host forwards it to `DashboardViewModel.remove`.
     */
    const val KEY_REMOVE_REQUEST = "remove_request"

    fun edit(packageName: String) = "edit/${Uri.encode(packageName)}"

    /**
     * Deep link to the edit screen, opened from outside the app's own navigation (the lock screen and the
     * "App locked" notification). The scheme is only ever used with explicit intents to MainActivity; it is
     * deliberately not registered in the manifest, so no other app can open it.
     */
    const val EDIT_DEEP_LINK = "focuslock://edit/{$ARG_PACKAGE}"

    fun editUri(packageName: String): Uri = Uri.parse("focuslock://edit/${Uri.encode(packageName)}")
}
