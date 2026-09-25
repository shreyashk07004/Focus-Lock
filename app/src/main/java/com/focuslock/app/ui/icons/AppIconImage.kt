package com.focuslock.app.ui.icons

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/** The launcher icon of [packageName] in a softly rounded square, loaded lazily through Coil. */
@Composable
fun AppIconImage(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = AppIcon(packageName),
        contentDescription = null, // decorative: the app name is always shown next to it
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f)),
    )
}
