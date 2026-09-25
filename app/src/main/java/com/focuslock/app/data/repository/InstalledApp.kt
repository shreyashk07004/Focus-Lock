package com.focuslock.app.data.repository

/**
 * An app the user could choose to limit. Deliberately holds no `Drawable`: icons are loaded
 * lazily by Coil from [packageName] (see `ui/icons/AppIcon`), keeping this class cheap and stable.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
)
