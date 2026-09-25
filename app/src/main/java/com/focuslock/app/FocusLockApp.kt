package com.focuslock.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.focuslock.app.ui.icons.AppIconFetcher
import com.focuslock.app.ui.icons.AppIconKeyer
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point; `@HiltAndroidApp` generates the app-level DI container.
 * Also supplies the app-wide Coil [ImageLoader] so installed-app icons can be loaded by package name.
 */
@HiltAndroidApp
class FocusLockApp : Application(), SingletonImageLoader.Factory {

    // Only the icon fetcher is registered: no network component is on the classpath (local-only app).
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory())
            }
            .build()
}
