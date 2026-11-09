package com.pvorg.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pvorg.android.R
import com.pvorg.android.data.LoadProgress
import com.pvorg.android.domain.Format
import com.pvorg.android.domain.MappingKind
import com.pvorg.android.state.AppViewModel
import com.pvorg.android.state.Page
import com.pvorg.android.state.Phase
import com.pvorg.android.state.UiState
import com.pvorg.android.state.ViewerMode
import com.pvorg.android.ui.components.ActionIcon
import com.pvorg.android.ui.components.ConfirmDeleteDialog
import com.pvorg.android.ui.components.StaleCleanupDialog
import com.pvorg.android.ui.screens.FiltersScreen
import com.pvorg.android.ui.screens.LibraryScreen
import com.pvorg.android.ui.screens.MappingScreen
import com.pvorg.android.ui.screens.SettingsScreen
import com.pvorg.android.ui.screens.ViewerScreen
import com.pvorg.android.ui.theme.Spacing
import com.pvorg.android.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Global actions shown in every page's overflow menu (spec §4). */
data class GlobalActions(
    val onReload: () -> Unit,
    val onCleanUp: () -> Unit,
    val onChooseFolder: () -> Unit,
)

@Composable
fun PvorgApp(viewModel: AppViewModel, onChooseFolder: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigationExpanded by viewModel.navigationExpanded.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { toast ->
            snackbarHost.currentSnackbarData?.dismiss()
            launch {
                val timer = launch {
                    delay(if (toast.action != null) 6_000 else 2_600)
                    snackbarHost.currentSnackbarData?.dismiss()
                }
                val result = snackbarHost.showSnackbar(toast.message, toast.actionLabel, duration = SnackbarDuration.Indefinite)
                timer.cancel()
                if (result == SnackbarResult.ActionPerformed) toast.action?.invoke()
            }
        }
    }

    val actions = GlobalActions(
        onReload = viewModel::reload,
        onCleanUp = viewModel::requestStaleCleanup,
        onChooseFolder = onChooseFolder,
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            when (val phase = state.phase) {
                Phase.NeedsFolder -> WelcomeScreen(onChooseFolder)
                is Phase.Loading -> ProgressScreen(phase.progress)
                is Phase.Failed -> ErrorScreen(phase, onRetry = viewModel::reload, onChooseFolder = onChooseFolder)
                Phase.Ready -> MainLayout(state, viewModel, navigationExpanded, actions)
            }
            SnackbarHost(
                snackbarHost,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = Spacing.lg),
            ) { data ->
                Snackbar(data, modifier = Modifier.widthIn(max = 560.dp))
            }
        }
    }

    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            confirmButton = { TextButton(onClick = viewModel::dismissNotice) { Text("OK") } },
            title = { Text(notice.title) },
            text = { Text(notice.message) },
        )
    }
    state.stalePreview?.let { preview ->
        StaleCleanupDialog(preview, onCancel = viewModel::dismissStaleCleanup, onConfirm = viewModel::confirmStaleCleanup)
    }
    if (state.pendingDeletion.isNotEmpty()) {
        ConfirmDeleteDialog(state, onCancel = viewModel::cancelDelete, onConfirm = viewModel::confirmDelete)
    }
    state.confirmsRevert?.let { kind ->
        val count = state.session(kind).changeCount
        AlertDialog(
            onDismissRequest = viewModel::dismissRevert,
            title = { Text("Discard ${Format.counted(count, "Unsaved Change")}?") },
            text = { Text("Cells go back to their saved decisions.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRevert) {
                    Text("Discard Changes", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissRevert) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MainLayout(state: UiState, viewModel: AppViewModel, navigationExpanded: Boolean, actions: GlobalActions) {
    BackHandler(enabled = state.selecting || state.page != Page.VIEWER || state.viewerMode == ViewerMode.VIDEO) {
        when {
            state.selecting -> viewModel.clearSelection()
            state.page != Page.VIEWER -> viewModel.showPage(Page.VIEWER)
            else -> viewModel.setViewerMode(ViewerMode.CONTACT_SHEET)
        }
    }
    // Leaving a page drops search focus and the soft keyboard with it.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.page) { focusManager.clearFocus() }
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        NavigationPane(
            state = state,
            expanded = navigationExpanded,
            onExpandedChange = viewModel::setNavigationExpanded,
            onSelect = viewModel::showPage,
        )
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (state.page) {
                Page.VIEWER -> ViewerScreen(state, viewModel, actions)
                Page.FILTERS -> FiltersScreen(state, viewModel, actions)
                Page.LIBRARY -> LibraryScreen(state, viewModel, actions)
                Page.GALLERY_MAPPING -> MappingScreen(MappingKind.GALLERIES, state, viewModel, actions)
                Page.TAG_MAPPING -> MappingScreen(MappingKind.TAGS, state, viewModel, actions)
                Page.SETTINGS -> SettingsScreen(state, viewModel, actions)
            }
        }
    }
}

private val Page.icon: ImageVector
    get() = when (this) {
        Page.VIEWER -> Icons.Outlined.SmartDisplay
        Page.FILTERS -> Icons.Outlined.FilterList
        Page.LIBRARY -> Icons.Outlined.GridView
        Page.GALLERY_MAPPING -> Icons.Outlined.Collections
        Page.TAG_MAPPING -> Icons.Outlined.Sell
        Page.SETTINGS -> Icons.Outlined.Settings
    }

private fun UiState.badge(page: Page): Int = page.mappingKind?.let { session(it).changeCount } ?: 0

/** Grouped navigation (spec §4): a drawer with labels and badges, collapsible to a rail. */
@Composable
private fun NavigationPane(state: UiState, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onSelect: (Page) -> Unit) {
    val library = state.library
    val summary = library?.let { "${Format.counted(it.videos.size, "video")} · ${Format.counted(it.galleries.size, "gallery", "galleries")}" }
    val main = Page.entries.filter { it.group != Page.Group.NONE }
    if (expanded) {
        Column(
            Modifier
                .width(256.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(Icons.AutoMirrored.Outlined.MenuOpen, "Collapse navigation", onClick = { onExpandedChange(false) })
                // The app's name (spec §4.2) wraps to a second line rather than clip at large text sizes.
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = Spacing.xs, end = Spacing.sm),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Page.Group.entries.filter { it != Page.Group.NONE }.forEach { group ->
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.xs)
                        .semantics { heading() },
                )
                main.filter { it.group == group }.forEach { page -> DrawerItem(page, state, onSelect) }
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
            DrawerItem(Page.SETTINGS, state, onSelect)
            if (summary != null) {
                Row(
                    Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary, style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            header = { ActionIcon(Icons.Outlined.Menu, "Expand navigation", onClick = { onExpandedChange(true) }) },
        ) {
            main.forEachIndexed { index, page ->
                if (index > 0 && main[index - 1].group != page.group) HorizontalDivider(Modifier.width(48.dp).padding(vertical = Spacing.sm))
                RailItem(page, state, onSelect)
            }
            Spacer(Modifier.weight(1f))
            RailItem(Page.SETTINGS, state, onSelect)
            if (library != null) {
                Text(
                    "${Format.number(library.videos.size)}\nvideos",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(page: Page, state: UiState, onSelect: (Page) -> Unit) {
    val badge = state.badge(page)
    NavigationDrawerItem(
        label = { Text(page.title) },
        icon = { Icon(page.icon, contentDescription = null) },
        selected = state.page == page,
        onClick = { onSelect(page) },
        badge = if (badge > 0) ({ Badge { Text(Format.number(badge)) } }) else null,
        modifier = Modifier.height(48.dp),
    )
}

@Composable
private fun RailItem(page: Page, state: UiState, onSelect: (Page) -> Unit) {
    val badge = state.badge(page)
    NavigationRailItem(
        selected = state.page == page,
        onClick = { onSelect(page) },
        icon = {
            BadgedBox(badge = { if (badge > 0) Badge { Text(Format.number(badge)) } }) {
                Icon(page.icon, contentDescription = page.title)
            }
        },
        label = { Text(page.title.substringBefore(' '), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** Overflow menu with the global commands (spec §4, §7.1). */
@Composable
fun OverflowMenu(actions: GlobalActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        ActionIcon(Icons.Outlined.MoreVert, "More", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Reload Library") }, onClick = { open = false; actions.onReload() })
            DropdownMenuItem(text = { Text("Clean Up Stale Entries…") }, onClick = { open = false; actions.onCleanUp() })
            DropdownMenuItem(text = { Text("Choose Library Folder…") }, onClick = { open = false; actions.onChooseFolder() })
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) { content() }
    }
}

/** First run: Android reads a library from a folder the user picks (spec §15.1). */
@Composable
private fun WelcomeScreen(onChooseFolder: () -> Unit) = CenteredColumn {
    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
    Text("Choose Your Library Folder", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        "Pick the folder that contains pvorg.json, a VID folder with your videos (contact sheets in VID/img) and a GAL folder with one folder per gallery.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.sm))
    Button(onClick = onChooseFolder) { Text("Choose Folder") }
}

/** Spec §6.6: step title, progress, and a detail line that's always reserved. */
@Composable
private fun ProgressScreen(progress: LoadProgress) = CenteredColumn {
    Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(progress.title, style = MaterialTheme.typography.headlineSmall)
    if (progress.fraction != null) {
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.width(320.dp).padding(top = Spacing.xs))
    } else {
        LinearProgressIndicator(modifier = Modifier.width(320.dp).padding(top = Spacing.xs))
    }
    Text(
        progress.detail ?: " ",
        style = MaterialTheme.typography.bodyMedium.tabular(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Spec §6.7. */
@Composable
private fun ErrorScreen(phase: Phase.Failed, onRetry: () -> Unit, onChooseFolder: () -> Unit) = CenteredColumn {
    Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
    Text("Couldn’t Load Your Library", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(phase.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    phase.folderName?.let {
        Text(
            "PP Video Organizer reads its configuration from pvorg.json in the folder “$it”.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Button(onClick = onRetry) { Text("Try Again") }
        OutlinedButton(onClick = onChooseFolder) { Text("Choose Another Folder") }
    }
}
