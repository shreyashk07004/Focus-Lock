package com.focuslock.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.focuslock.app.ui.common.formatDuration
import com.focuslock.app.ui.common.formatUsed
import com.focuslock.app.ui.icons.AppIconImage
import com.focuslock.app.ui.theme.FocusLockTheme

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun DashboardScreen(
    onAddApp: () -> Unit,
    onEditApp: (packageName: String) -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteRequest by viewModel.deleteRequest.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // State-driven rather than event-driven: the snackbar re-appears if the user comes back to this
    // screen while the undo window is still open, and disappears the moment the window closes.
    val deletedMessage = undo?.let { stringResource(R.string.removed_snackbar, it.app.appLabel) }
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(undo) {
        if (deletedMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Indefinite, // the ViewModel's timer decides when it goes away
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    }

    DashboardContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onAddApp = onAddApp,
        onEditApp = onEditApp,
        onOpenPermissions = onOpenPermissions,
        onToggle = viewModel::setEnabled,
        onSwipeDelete = viewModel::requestDelete,
    )

    deleteRequest?.let { app ->
        ConfirmRemoveDialog(
            appLabel = app.appLabel,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    state: DashboardUiState,
    snackbarHostState: SnackbarHostState,
    onAddApp: () -> Unit,
    onEditApp: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onToggle: (packageName: String, enabled: Boolean) -> Unit,
    onSwipeDelete: (BlockedApp) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hasApps = state is DashboardUiState.Content && state.apps.isNotEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenPermissions) {
                        Icon(Icons.Rounded.Shield, contentDescription = stringResource(R.string.permissions_open_desc))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            // With no apps the empty state carries its own, bigger call to action.
            if (hasApps) {
                ExtendedFloatingActionButton(
                    onClick = onAddApp,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_app)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (state) {
            DashboardUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DashboardUiState.Content ->
                if (state.apps.isEmpty()) {
                    Column(modifier = Modifier.padding(innerPadding)) {
                        if (!state.trackingReady) {
                            SetupBanner(
                                missing = state.missingSetup,
                                onReview = onOpenPermissions,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                            )
                        }
                        EmptyState(onAddApp = onAddApp, modifier = Modifier.weight(1f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            end = 16.dp,
                            // Room for the FAB so it never covers the last card.
                            bottom = innerPadding.calculateBottomPadding() + 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!state.trackingReady) {
                            item(key = "setup-banner") {
                                SetupBanner(
                                    missing = state.missingSetup,
                                    onReview = onOpenPermissions,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        if (state.missingBlocking.isNotEmpty()) {
                            item(key = "blocking-banner") {
                                BlockingBanner(
                                    missing = state.missingBlocking,
                                    onReview = onOpenPermissions,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        items(state.apps, key = { it.app.packageName }) { item ->
                            BlockedAppCard(
                                item = item,
                                showUsage = state.trackingReady,
                                onClick = { onEditApp(item.app.packageName) },
                                onToggle = { onToggle(item.app.packageName, it) },
                                onSwipeDelete = { onSwipeDelete(item.app) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
        }
    }
}

/** Explains that tracking is off and what is still needed, with a way straight to the checklist. */
@Composable
private fun SetupBanner(
    missing: List<EssentialPermission>,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `map` is inline, so it may call stringResource; joinToString's lambda may not.
    val names = missing.map {
        stringResource(
            when (it) {
                EssentialPermission.USAGE_ACCESS -> R.string.perm_usage_title
                EssentialPermission.NOTIFICATIONS -> R.string.perm_notifications_title
            },
        )
    }.joinToString(", ")
    NoticeBanner(
        title = stringResource(R.string.setup_banner_title),
        body = stringResource(R.string.setup_banner_body, names),
        onReview = onReview,
        modifier = modifier,
    )
}

/** Explains that apps are counted but can't be locked yet, and what to allow to change that. */
@Composable
private fun BlockingBanner(
    missing: List<BlockingPermission>,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = missing.map {
        stringResource(
            when (it) {
                BlockingPermission.ACCESSIBILITY -> R.string.perm_accessibility_title
                BlockingPermission.OVERLAY -> R.string.perm_overlay_title
            },
        )
    }.joinToString(", ")
    NoticeBanner(
        title = stringResource(R.string.blocking_banner_title),
        body = stringResource(R.string.blocking_banner_body, names),
        onReview = onReview,
        modifier = modifier,
    )
}

@Composable
private fun NoticeBanner(
    title: String,
    body: String,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onReview,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.setup_banner_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedAppCard(
    item: AppUsageItem,
    showUsage: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = item.app
    // The dismiss state outlives recompositions, so it must not capture a stale callback.
    val currentOnSwipeDelete by rememberUpdatedState(onSwipeDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) currentOnSwipeDelete()
            // Never accept the swipe: the card springs back and the dialog decides whether it goes.
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            SwipeDeleteBackground(revealed = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
        },
    ) {
        Card(
            onClick = onClick,
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .heightIn(min = 72.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // A paused rule is dimmed, but its switch stays at full strength: it is the way back.
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (app.isEnabled) 1f else 0.6f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppIconImage(packageName = app.packageName, size = 52.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appLabel,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val limit = formatDuration(app.dailyLimitMinutes)
                            Text(
                                text = stringResource(
                                    if (app.isEnabled) R.string.limit_per_day else R.string.limit_per_day_paused,
                                    limit,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val toggleDescription = stringResource(R.string.toggle_limit_desc, app.appLabel)
                    Switch(
                        checked = app.isEnabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.semantics { contentDescription = toggleDescription },
                    )
                }

                // Only a rule that is on and actually being tracked has progress worth showing.
                if (showUsage && app.isEnabled) {
                    UsageProgress(
                        item = item,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
}

/** Used-versus-limit bar, with the time left, or a "Locked" label once the allowance is spent. */
@Composable
private fun UsageProgress(item: AppUsageItem, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(targetValue = item.fraction, label = "usage-progress")
    val barColor = if (item.isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val used = formatUsed(item.usedSeconds)
    val progressDescription = stringResource(R.string.usage_progress_desc, used, formatDuration((item.limitSeconds / 60).toInt()))

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .semantics { contentDescription = progressDescription },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.usage_used, used),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.isLocked) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = stringResource(R.string.usage_locked),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            } else {
                // Rounded up, so "0 min left" never shows while there is still time.
                val minutesLeft = ((item.remainingSeconds + 59) / 60).toInt()
                Text(
                    text = stringResource(R.string.usage_left, formatDuration(minutesLeft)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Says why the bar's total is longer than the daily limit. Stays all day, even once the extra is used.
        if (item.hasExtra) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.usage_extra, formatDuration((item.extraSeconds / 60).toInt())),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

/** Red panel with a bin icon that fades in only while the card is being dragged. */
@Composable
private fun SwipeDeleteBackground(revealed: Boolean) {
    val color by animateColorAsState(
        targetValue = if (revealed) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
        label = "swipe-delete-background",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CardShape)
            .background(color)
            .padding(end = 28.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (revealed) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun EmptyState(onAddApp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.HourglassEmpty,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAddApp,
            modifier = Modifier.height(56.dp),
            contentPadding = PaddingValues(horizontal = 28.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_first_app))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    FocusLockTheme {
        DashboardContent(
            state = DashboardUiState.Content(
                listOf(
                    AppUsageItem(
                        BlockedApp("com.example.chat", "Chat", 10, true, 0),
                        DailyUsage(
                            packageName = "com.example.chat", dateKey = "2026-01-01", usedSeconds = 250,
                            bonusSeconds = 15 * 60L, grantedSeconds = 15 * 60L,
                        ),
                    ),
                    AppUsageItem(
                        BlockedApp("com.example.news", "News", 15, true, 0),
                        DailyUsage(packageName = "com.example.news", dateKey = "2026-01-01", usedSeconds = 15 * 60L, isLockedToday = true),
                    ),
                    AppUsageItem(BlockedApp("com.example.video", "Video Player", 90, false, 0)),
                ),
                missingBlocking = listOf(BlockingPermission.ACCESSIBILITY),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAddApp = {},
            onEditApp = {},
            onOpenPermissions = {},
            onToggle = { _, _ -> },
            onSwipeDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardSetupNeededPreview() {
    FocusLockTheme {
        DashboardContent(
            state = DashboardUiState.Content(
                apps = listOf(AppUsageItem(BlockedApp("com.example.chat", "Chat", 10, true, 0))),
                missingSetup = listOf(EssentialPermission.USAGE_ACCESS, EssentialPermission.NOTIFICATIONS),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAddApp = {},
            onEditApp = {},
            onOpenPermissions = {},
            onToggle = { _, _ -> },
            onSwipeDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    FocusLockTheme {
        DashboardContent(
            state = DashboardUiState.Content(emptyList()),
            snackbarHostState = remember { SnackbarHostState() },
            onAddApp = {},
            onEditApp = {},
            onOpenPermissions = {},
            onToggle = { _, _ -> },
            onSwipeDelete = {},
        )
    }
}
