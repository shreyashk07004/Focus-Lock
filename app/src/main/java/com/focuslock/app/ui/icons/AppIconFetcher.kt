package com.focuslock.app.ui.icons

import android.content.pm.PackageManager
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/**
 * Coil can't load an installed app's icon by itself: there is no URL or file, only a
 * [PackageManager] lookup. This fetcher bridges that gap. The lookup runs on Coil's fetcher
 * dispatcher (off the main thread) and results are memory-cached per [AppIcon].
 */
class AppIconFetcher(
    private val data: AppIcon,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val drawable = options.context.packageManager.getApplicationIcon(data.packageName)
        return ImageFetchResult(
            // Adaptive icons are painted through Drawable.draw(), so masks/layers render correctly.
            image = drawable.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<AppIcon> {
        override fun create(data: AppIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(data, options)
    }
}

/** Stable memory-cache key. Without a Keyer Coil would not cache custom models at all. */
class AppIconKeyer : Keyer<AppIcon> {
    override fun key(data: AppIcon, options: Options): String = "app-icon:${data.packageName}"
}
