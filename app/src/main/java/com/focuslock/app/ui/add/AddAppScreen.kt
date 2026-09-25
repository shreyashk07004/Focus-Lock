package com.focuslock.app.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.R
import com.focuslock.app.data.repository.InstalledApp
import com.focuslock.app.ui.common.LimitEditor
import com.focuslock.app.ui.common.LimitInput
import com.focuslock.app.ui.icons.AppIconImage
import com.focuslock.app.ui.theme.FocusLockTheme

/**
 * Two steps on one screen: pick an app (searchable), then set its daily limit and save.
 * System Back from the second step returns to the list instead of leaving the screen.
 */
@Composable
fun AddAppScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddAppViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val limitText by viewModel.limitText.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }

    val selected = (state as? AddAppUiState.Ready)?.selected
    BackHandler(enabled = selected != null) { viewModel.onDeselect() }

    AddAppContent(
        state = state,
        query = query,
        limitText = limitText,
        onBack = { if (selected != null) viewModel.onDeselect() else onBack() },
        onQueryChange = viewModel::onQueryChange,
        onSelect = viewModel::onSelect,
        onLimitTextChange = viewModel::onLimitTextChange,
        onSave = viewModel::save,
        onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppContent(
    state: AddAppUiState,
    query: String,
    limitText: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (InstalledApp) -> Unit,
    onLimitTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    val selected = (state as? AddAppUiState.Ready)?.selected

    Scaffold(
        // Lifts the whole screen (including the pinned Save button) above the keyboard.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (selected != null) R.string.add_limit_title else R.string.add_pick_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            if (selected != null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = onSave,
                        enabled = LimitInput.parse(limitText) != null,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .fillMaxWidth()
                            .height(56.dp),
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                AddAppUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                AddAppUiState.Error -> Message(
                    text = stringResource(R.string.add_load_error),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                )

                is AddAppUiState.Ready ->
                    if (state.selected != null) {
                        SetLimitStep(
                            app = state.selected,
                            limitText = limitText,
                            onLimitTextChange = onLimitTextChange,
                        )
                    } else {
                        PickAppStep(
                            state = state,
                            query = query,
                            onQueryChange = onQueryChange,
                            onSelect = onSelect,
                        )
                    }
            }
        }
    }
}

@Composable
private fun PickAppStep(
    state: AddAppUiState.Ready,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (InstalledApp) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_apps_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )

        when {
            state.noAppsLeft -> Message(stringResource(R.string.add_no_apps_left))
            state.apps.isEmpty() -> Message(stringResource(R.string.search_no_match, query.trim()))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onClick = {
                            focusManager.clearFocus() // hide the keyboard before the next step appears
                            onSelect(app)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIconImage(packageName = app.packageName, size = 44.dp)
        Column {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
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
}

@Composable
private fun SetLimitStep(
    app: InstalledApp,
    limitText: String,
    onLimitTextChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIconImage(packageName = app.packageName, size = 64.dp)
            Column {
                Text(text = app.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                LimitEditor(
                    text = limitText,
                    onTextChange = onLimitTextChange,
                    onSliderChange = { onLimitTextChange(it.toString()) },
                    onSliderFinished = {}, // nothing to save yet: the Save button commits
                )
            }
        }

        Text(
            text = stringResource(R.string.add_limit_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Message(
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PickAppPreview() {
    FocusLockTheme {
        AddAppContent(
            state = AddAppUiState.Ready(
                apps = listOf(InstalledApp("com.example.chat", "Chat"), InstalledApp("com.example.video", "Video Player")),
                noAppsLeft = false,
                selected = null,
            ),
            query = "",
            limitText = "30",
            onBack = {}, onQueryChange = {}, onSelect = {}, onLimitTextChange = {}, onSave = {}, onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetLimitPreview() {
    FocusLockTheme {
        AddAppContent(
            state = AddAppUiState.Ready(
                apps = emptyList(),
                noAppsLeft = false,
                selected = InstalledApp("com.example.chat", "Chat"),
            ),
            query = "",
            limitText = "45",
            onBack = {}, onQueryChange = {}, onSelect = {}, onLimitTextChange = {}, onSave = {}, onRetry = {},
        )
    }
}
