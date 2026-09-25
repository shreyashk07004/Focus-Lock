package com.focuslock.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.focuslock.app.R

/** "45 min", "2 h" or "1 h 30 min". */
@Composable
fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.duration_min, rest)
        rest == 0 -> stringResource(R.string.duration_h, hours)
        else -> stringResource(R.string.duration_h_min, hours, rest)
    }
}

/**
 * Time already used, precise enough to watch it grow: "45 s", "12 min 30 s", and above an hour "1 h 5 min".
 * (Limits use [formatDuration]; they are whole minutes, so seconds would only be noise there.)
 */
@Composable
fun formatUsed(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    return when {
        total < 60 -> stringResource(R.string.duration_s, total.toInt())
        total < 3600 -> stringResource(R.string.duration_min_s, (total / 60).toInt(), (total % 60).toInt())
        else -> formatDuration((total / 60).toInt())
    }
}
