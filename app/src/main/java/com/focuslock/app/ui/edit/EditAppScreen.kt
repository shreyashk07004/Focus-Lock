package com.focuslock.app.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.R
import com.focuslock.app.data.db.BlockedApp
import com.focuslock.app.data.db.DailyUsage
import com.focuslock.app.ui.common.ConfirmRemoveDialog
import com.focuslock.app.ui.common.LimitEditor
import com.focuslock.app.ui.common.LimitInput
import com.focuslock.app.ui.common.formatDuration
import com.focuslock.app.ui.common.formatUsed
import com.focuslock.app.ui.icons.AppIconImage
import com.focuslock.app.ui.theme.FocusLockTheme

@Composable
fun EditAppScreen(
    onBack: () -> Unit,
    onRemoveConfirmed: (packageName: String) -> Unit,
    viewModel: EditAppViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EditAppContent(
        state = state,
        onBack = onBack,
        onEnabledChange = viewModel::setEnabled,
        onLimitChange = viewModel::setLimit,
        onUnlock = viewModel::unlock,
        onRemoveConfirmed = onRemoveConfirmed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAppContent(
    state: EditAppUiState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLimitChange: (Int) -> Unit,
    onUnlock: (bonusMinutes: Int) -> Unit,
    onRemoveConfirmed: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? EditAppUiState.Content)?.app?.appLabel.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                EditAppUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                EditAppUiState.NotFound -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.edit_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onBack) { Text(stringResource(R.string.back)) }
                }

                is EditAppUiState.Content -> EditForm(
                    content = state,
                    onEnabledChange = onEnabledChange,
                    onLimitChange = onLimitChange,
                    onUnlock = onUnlock,
                    onRemoveConfirmed = onRemoveConfirmed,
                )
            }
        }
    }
}

@Composable
private fun EditForm(
    content: EditAppUiState.Content,
    onEnabledChange: (Boolean) -> Unit,
    onLimitChange: (Int) -> Unit,
    onUnlock: (bonusMinutes: Int) -> Unit,
    onRemoveConfirmed: (String) -> Unit,
) {
    val app = content.app
    // Local, transient text of the limit field, seeded once from the stored value. It is not
    // re-synced from Room on purpose: each save re-emits the row, and that must not clobber a
    // half-typed number. Only valid values are ever written to Room.
    var limitText by rememberSaveable(app.packageName) { mutableStateOf(app.dailyLimitMinutes.toString()) }
    var showRemoveDialog by rememberSaveable { mutableStateOf(false) }

    fun saveIfValid() {
        LimitInput.parse(limitText)?.let { if (it != app.dailyLimitMinutes) onLimitChange(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIconImage(packageName = app.packageName, size = 72.dp)
            Column {
                Text(
                    text = app.appLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // What was granted today and how much of it is left, live as the app is used.
        if (content.hasExtra) {
            ExtraTimeCard(
                extraSeconds = content.extraSeconds,
                remainingSeconds = content.remainingSeconds,
                isLocked = content.isLocked,
            )
        }

        // The emergency way back in. Only shown while the app is actually locked.
        if (content.isLocked) {
            UnlockCard(
                usedSeconds = content.usedSeconds,
                limitSeconds = content.limitSeconds,
                onUnlock = onUnlock,
            )
        }

        // On/off. The whole row is the tap target, not just the switch.
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .toggleable(value = app.isEnabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (app.isEnabled) R.string.edit_limit_on else R.string.edit_limit_paused),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            if (app.isEnabled) R.string.edit_limit_on_desc else R.string.edit_limit_paused_desc,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = app.isEnabled, onCheckedChange = null) // the row above handles the tap
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.daily_limit),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!content.canEditLimit) {
                    // Changing it now would double-count the extra time, so it is fixed until tomorrow.
                    Text(
                        text = stringResource(R.string.edit_limit_locked_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LimitEditor(
                    enabled = content.canEditLimit,
                    text = limitText,
                    onTextChange = {
                        limitText = it
                        saveIfValid() // typing saves as soon as the number is valid
                    },
                    onSliderChange = { limitText = it.toString() }, // dragging only moves the thumb...
                    onSliderFinished = { saveIfValid() }, // ...and releasing saves, one write per gesture
                )
            }
        }

        Text(
            text = stringResource(R.string.edit_autosave),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = { showRemoveDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.edit_remove))
        }
    }

    if (showRemoveDialog) {
        ConfirmRemoveDialog(
            appLabel = app.appLabel,
            onConfirm = {
                showRemoveDialog = false
                onRemoveConfirmed(app.packageName)
            },
            onDismiss = { showRemoveDialog = false },
        )
    }
}

/**
 * Shown all day once extra time was granted: how much, and how much of the whole allowance is left right
 * now (it counts down while the app is used, because the usage service keeps the row up to date).
 */
@Composable
private fun ExtraTimeCard(
    extraSeconds: Long,
    remainingSeconds: Long,
    isLocked: Boolean,
) {
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.MoreTime, contentDescription = null, tint = onContainer)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.edit_extra_title, formatDuration((extraSeconds / 60).toInt())),
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                )
                Text(
                    text = if (isLocked) {
                        stringResource(R.string.edit_extra_used_up)
                    } else {
                        // Rounded up, like the dashboard, so "0 min left" never shows while there is still time.
                        stringResource(R.string.usage_left, formatDuration(((remainingSeconds + 59) / 60).toInt()))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
            }
        }
    }
}

/** Bonus options offered by "Unlock now", in minutes. */
private val UnlockBonusOptions = listOf(5, 15, 30, 60)
private const val DefaultUnlockBonus = 15

/**
 * "Locked for today" panel with the emergency unlock. The extra time is needed because the app has already
 * used more than its limit; it is added to today's allowance only and is gone tomorrow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnlockCard(
    usedSeconds: Long,
    limitSeconds: Long,
    onUnlock: (bonusMinutes: Int) -> Unit,
) {
    var bonus by rememberSaveable { mutableIntStateOf(DefaultUnlockBonus) }
    val onContainer = MaterialTheme.colorScheme.onErrorContainer

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = onContainer)
                Text(
                    text = stringResource(R.string.edit_locked_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = onContainer,
                )
            }
            Text(
                text = stringResource(
                    R.string.edit_locked_body,
                    formatUsed(usedSeconds),
                    formatDuration((limitSeconds / 60).toInt()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
            Text(
                text = stringResource(R.string.edit_unlock_extra),
                style = MaterialTheme.typography.titleSmall,
                color = onContainer,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in UnlockBonusOptions) {
                    FilterChip(
                        selected = bonus == option,
                        onClick = { bonus = option },
                        label = { Text(stringResource(R.string.edit_unlock_chip, option)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.edit_unlock_hint, formatDuration(bonus)),
                style = MaterialTheme.typography.bodySmall,
                color = onContainer,
            )
            Button(
                onClick = { onUnlock(bonus) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edit_unlock_button))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditAppPreview() {
    FocusLockTheme {
        EditAppContent(
            state = EditAppUiState.Content(BlockedApp("com.example.chat", "Chat", 45, true, 0)),
            onBack = {}, onEnabledChange = {}, onLimitChange = {}, onUnlock = {}, onRemoveConfirmed = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditAppLockedPreview() {
    FocusLockTheme {
        EditAppContent(
            state = EditAppUiState.Content(
                BlockedApp("com.example.chat", "Chat", 45, true, 0),
                DailyUsage(packageName = "com.example.chat", dateKey = "2026-01-01", usedSeconds = 2_705, isLockedToday = true),
            ),
            onBack = {}, onEnabledChange = {}, onLimitChange = {}, onUnlock = {}, onRemoveConfirmed = {},
        )
    }
}
