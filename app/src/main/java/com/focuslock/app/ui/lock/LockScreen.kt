package com.focuslock.app.ui.lock

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focuslock.app.R
import com.focuslock.app.ui.common.formatDuration
import com.focuslock.app.ui.common.formatUsed
import com.focuslock.app.ui.icons.AppIconImage
import com.focuslock.app.ui.theme.FocusLockTheme
import kotlinx.coroutines.delay
import java.util.Date

/** The whole lock page. Only [LockUiState.Locked] draws anything; the other states are a blank moment before closing. */
@Composable
fun LockScreen(
    state: LockUiState,
    resetAtMs: Long,
    onManage: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state is LockUiState.Locked) LockContent(state, resetAtMs, onManage)
    }
}

@Composable
private fun LockContent(state: LockUiState.Locked, resetAtMs: Long, onManage: () -> Unit) {
    // Redraw every so often so "in 5 h 12 min" counts down while the page stays open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    val context = LocalContext.current
    val resetTime = DateFormat.getTimeFormat(context).format(Date(resetAtMs))
    val minutesLeft = (((resetAtMs - now) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        AppIconImage(packageName = state.packageName, size = 96.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = state.label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(32.dp))
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                R.string.lock_used_of_limit,
                formatUsed(state.usedSeconds),
                formatDuration((state.limitSeconds / 60).toInt()),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.lock_resets, resetTime, formatDuration(minutesLeft)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        // The only action on this page. It leads into FocusLock, not back into the blocked app.
        Button(
            onClick = onManage,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.lock_manage))
        }
    }
}

private const val TICK_MS = 30_000L

@Preview(showBackground = true)
@Composable
private fun LockPreview() {
    FocusLockTheme {
        LockScreen(
            state = LockUiState.Locked("com.example.chat", "Chat", usedSeconds = 63, limitSeconds = 60),
            resetAtMs = System.currentTimeMillis() + 5 * 3_600_000L,
            onManage = {},
        )
    }
}
