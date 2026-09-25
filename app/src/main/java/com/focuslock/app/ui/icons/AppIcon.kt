package com.focuslock.app.ui.icons

/**
 * Coil model for "the launcher icon of this installed app".
 * Pass it to `AsyncImage(model = AppIcon(packageName), ...)`; see [AppIconFetcher].
 */
data class AppIcon(val packageName: String)
