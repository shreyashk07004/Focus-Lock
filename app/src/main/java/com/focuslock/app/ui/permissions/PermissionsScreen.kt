package com.focuslock.app.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.R
import com.focuslock.app.data.permissions.PermissionStatus
import com.focuslock.app.data.permissions.SettingsIntents
import com.focuslock.app.ui.theme.FocusLockTheme

private val CardShape = RoundedCornerShape(24.dp)

/** What one checklist row needs to draw itself; the screen fills these in from the current status. */
private data class PermissionRow(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val why: Int,
    /** True when the grant is only needed to block apps (Phase 5), not to track them. */
    val forBlocking: Boolean,
    val granted: Boolean,
    @param:StringRes val buttonLabel: Int,
    @param:StringRes val hint: Int? = null,
    val onGrant: () -> Unit,
)

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    // Android stops showing the notification dialog after the user declines it twice. When that happens
    // the button switches to opening the app's notification settings, which is the only way left.
    var notificationDialogExhausted by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refresh()
        if (!granted && activity != null) {
            notificationDialogExhausted = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    // The runtime dialog only exists on Android 13+, and is pointless if the permission is already
    // granted but notifications are switched off in Settings (then only Settings can help).
    val canAskNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !notificationDialogExhausted &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

    val rows = listOf(
        PermissionRow(
            id = "usage",
            title = R.string.perm_usage_title,
            why = R.string.perm_usage_why,
            forBlocking = false,
            granted = status.usageAccess,
            buttonLabel = R.string.perm_button_open_settings,
            onGrant = { SettingsIntents.launch(context, SettingsIntents.usageAccess(context)) },
        ),
        PermissionRow(
            id = "overlay",
            title = R.string.perm_overlay_title,
            why = R.string.perm_overlay_why,
            forBlocking = true,
            granted = status.overlay,
            buttonLabel = R.string.perm_button_open_settings,
            onGrant = { SettingsIntents.launch(context, SettingsIntents.overlay(context)) },
        ),
        PermissionRow(
            id = "accessibility",
            title = R.string.perm_accessibility_title,
            why = R.string.perm_accessibility_why,
            forBlocking = true,
            granted = status.accessibility,
            buttonLabel = R.string.perm_button_open_settings,
            // Sideloaded apps on Android 13+ start with this switch locked ("restricted setting").
            hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) R.string.perm_accessibility_hint else null,
            onGrant = { SettingsIntents.launch(context, SettingsIntents.accessibility()) },
        ),
        PermissionRow(
            id = "notifications",
            title = R.string.perm_notifications_title,
            why = R.string.perm_notifications_why,
            forBlocking = false,
            granted = status.notifications,
            buttonLabel = if (canAskNotifications) R.string.perm_button_allow else R.string.perm_button_open_settings,
            onGrant = {
                if (canAskNotifications) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    SettingsIntents.launch(context, SettingsIntents.appNotifications(context))
                }
            },
        ),
    )

    PermissionsContent(rows = rows, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsContent(rows: List<PermissionRow>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.permissions_intro),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.permissions_summary, rows.count { it.granted }, rows.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(rows, key = { it.id }) { row -> PermissionCard(row) }
            item {
                Text(
                    text = stringResource(R.string.permissions_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(row: PermissionRow) {
    val statusColor = if (row.granted) grantedColor() else MaterialTheme.colorScheme.error

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon plus word as well as colour, so the state is clear without telling red from green.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (row.granted) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        tint = statusColor,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(row.title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(if (row.granted) R.string.perm_granted else R.string.perm_missing),
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                        )
                        Text(
                            text = "·  " + stringResource(
                                if (row.forBlocking) R.string.perm_tag_blocking else R.string.perm_tag_tracking,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(row.why),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!row.granted) {
                row.hint?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = row.onGrant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(stringResource(row.buttonLabel))
                }
            }
        }
    }
}

/** A readable green for "granted": Material's colour scheme has no success colour of its own. */
@Composable
private fun grantedColor(): Color = if (isSystemInDarkTheme()) Color(0xFF7DDB92) else Color(0xFF1B7A3A)

@Preview(showBackground = true)
@Composable
private fun PermissionsPreview() {
    val status = PermissionStatus(usageAccess = true, notifications = false)
    FocusLockTheme(dynamicColor = false) {
        PermissionsContent(
            rows = listOf(
                PermissionRow("usage", R.string.perm_usage_title, R.string.perm_usage_why, false, status.usageAccess, R.string.perm_button_open_settings, onGrant = {}),
                PermissionRow("overlay", R.string.perm_overlay_title, R.string.perm_overlay_why, true, status.overlay, R.string.perm_button_open_settings, onGrant = {}),
                PermissionRow("notifications", R.string.perm_notifications_title, R.string.perm_notifications_why, false, status.notifications, R.string.perm_button_allow, onGrant = {}),
            ),
            onBack = {},
        )
    }
}
