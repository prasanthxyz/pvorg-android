package com.pvorg.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pvorg.android.domain.DisplayName
import com.pvorg.android.domain.Format
import com.pvorg.android.domain.Video
import com.pvorg.android.state.AppViewModel
import com.pvorg.android.state.UiState
import com.pvorg.android.ui.GlobalActions
import com.pvorg.android.ui.OverflowMenu
import com.pvorg.android.ui.components.ActionIcon
import com.pvorg.android.ui.components.Callout
import com.pvorg.android.ui.components.CalloutTone
import com.pvorg.android.ui.components.EmptyState
import com.pvorg.android.ui.components.PageTopBar
import com.pvorg.android.ui.components.SearchField
import com.pvorg.android.ui.components.SkippedFilesDialog
import com.pvorg.android.ui.components.Thumbnail
import com.pvorg.android.ui.theme.Radius
import com.pvorg.android.ui.theme.Spacing
import com.pvorg.android.ui.theme.Stage
import com.pvorg.android.ui.theme.StageTheme

@Composable
fun LibraryScreen(state: UiState, viewModel: AppViewModel, actions: GlobalActions) {
    val library = state.library ?: return
    val total = library.videos.size

    Column(Modifier.fillMaxSize()) {
        if (state.selecting) {
            // Contextual app bar for selection mode (spec §15.3).
            PageTopBar(
                title = "${Format.number(state.selection.size)} of ${Format.counted(total, "video")} selected",
                subtitle = null,
                navigationIcon = { ActionIcon(Icons.Outlined.Close, "Deselect All (Esc)", viewModel::clearSelection) },
                actions = {
                    ActionIcon(
                        Icons.Outlined.SelectAll,
                        if (state.libraryQuery.isBlank()) "Select All" else "Select All Matching",
                        viewModel::selectAllVisible,
                        enabled = state.selection.size < state.libraryVisible.size,
                    )
                    ActionIcon(
                        Icons.Outlined.Delete,
                        "Delete Selected",
                        { viewModel.requestDelete(state.selection) },
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        } else {
            PageTopBar(
                title = "Library",
                subtitle = if (state.libraryQuery.isBlank()) Format.counted(total, "video")
                else "${Format.number(state.libraryVisible.size)} of ${Format.counted(total, "video")}",
                actions = {
                    SearchField(state.libraryQuery, viewModel::setLibraryQuery, "Search videos", onEditingChange = { viewModel.isEditingText = it }, focusRequest = state.searchFocusRequest)
                    ActionIcon(Icons.Outlined.Checklist, "Select All", viewModel::selectAllVisible, enabled = state.libraryVisible.isNotEmpty())
                    ActionIcon(Icons.Outlined.Delete, "Delete Selected", {}, enabled = false)
                    OverflowMenu(actions)
                },
            )
        }

        when {
            library.videos.isEmpty() -> EmptyState(
                Icons.Outlined.Movie,
                "No Videos",
                if (library.skipped.isEmpty()) "Add videos named studio-cast-title to the VID folder of “${library.rootName}”, then reload the library."
                else "${Format.counted(library.skipped.size, "file was", "files were")} skipped because the names don’t follow the studio-cast-title format.",
            ) {
                Button(onClick = actions.onReload) { Text("Reload Library") }
                if (library.skipped.isNotEmpty()) OutlinedButton(onClick = { viewModel.showSkippedFiles(true) }) { Text("Show Skipped Files") }
            }
            state.libraryVisible.isEmpty() -> EmptyState(
                Icons.Outlined.SearchOff,
                "No Results for “${state.libraryQuery.trim()}”",
                "Check the spelling or try a new search.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                contentPadding = PaddingValues(Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (library.skipped.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "skipped") {
                        Callout(
                            CalloutTone.WARNING,
                            "${Format.counted(library.skipped.size, "file")} skipped",
                            "Names must be lowercase, without spaces, in the form studio-cast-title.",
                        ) { TextButton(onClick = { viewModel.showSkippedFiles(true) }) { Text("Show Files") } }
                    }
                }
                items(state.libraryVisible, key = { it.filename }) { video ->
                    VideoCard(
                        video = video,
                        selected = video.filename in state.selection,
                        onTap = { if (state.selecting) viewModel.toggleSelection(video.filename) else viewModel.showPreview(video.filename) },
                        onLongPress = { if (state.selecting) viewModel.toggleSelection(video.filename) else viewModel.startSelection(video.filename) },
                    )
                }
            }
        }
    }

    if (state.showsSkippedFiles) SkippedFilesDialog(library.skipped, onDismiss = { viewModel.showSkippedFiles(false) })
    state.libraryPreview?.let(library::video)?.let { video ->
        PreviewDialog(
            video = video,
            selected = video.filename in state.selection,
            onDismiss = { viewModel.showPreview(null) },
            onSelect = { viewModel.startSelection(video.filename); viewModel.showPreview(null) },
            onDelete = { viewModel.requestDelete(listOf(video.filename)) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(video: Video, selected: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val shape = RoundedCornerShape(Radius.medium)
    Column(
        Modifier
            .clip(shape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress, onLongClickLabel = "Select")
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = "${DisplayName.title(video)}, ${DisplayName.credits(video)}"
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Thumbnail(video.contactSheet, contentDescription = null, modifier = Modifier.fillMaxSize().clip(shape))
            Box(
                Modifier
                    .fillMaxSize()
                    .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f), shape),
            )
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.sm)
                        .size(28.dp)
                        .background(Color.White, CircleShape),
                )
            }
        }
        Column(Modifier.padding(horizontal = Spacing.xxs)) {
            Text(DisplayName.title(video), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                DisplayName.credits(video),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Touch replacement for the desktop hover preview (spec §15.3). */
@Composable
private fun PreviewDialog(video: Video, selected: Boolean, onDismiss: () -> Unit, onSelect: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        StageTheme {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Stage.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(DisplayName.title(video), style = MaterialTheme.typography.titleLarge, color = Stage.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(DisplayName.credits(video), style = MaterialTheme.typography.bodyMedium, color = Stage.secondary, maxLines = 1)
                    }
                    if (!selected) TextButton(onClick = onSelect) { Text("Select") }
                    TextButton(onClick = onDelete) { Text("Delete…", color = MaterialTheme.colorScheme.error) }
                    ActionIcon(Icons.Outlined.Close, "Close", onDismiss, tint = Stage.primary)
                }
                Thumbnail(
                    video.contactSheet,
                    contentDescription = "Contact sheet for ${DisplayName.title(video)}",
                    contentScale = ContentScale.Fit,
                    placeholder = Stage.raised,
                    modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Radius.large)),
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(video.filename, style = MaterialTheme.typography.bodySmall, color = Stage.secondary, modifier = Modifier.width(800.dp))
            }
        }
    }
}
