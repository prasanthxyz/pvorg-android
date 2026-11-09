package com.pvorg.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pvorg.android.domain.DisplayName
import com.pvorg.android.domain.Format
import com.pvorg.android.domain.MappingKind
import com.pvorg.android.domain.MappingRow
import com.pvorg.android.domain.TriState
import com.pvorg.android.state.AppViewModel
import com.pvorg.android.state.MappingSession
import com.pvorg.android.state.UiState
import com.pvorg.android.ui.GlobalActions
import com.pvorg.android.ui.OverflowMenu
import com.pvorg.android.ui.components.ActionIcon
import com.pvorg.android.ui.components.EmptyState
import com.pvorg.android.ui.components.PageTopBar
import com.pvorg.android.ui.components.SearchField
import com.pvorg.android.ui.components.Thumbnail
import com.pvorg.android.ui.components.TriStateBadge
import com.pvorg.android.ui.components.icon
import com.pvorg.android.ui.theme.LocalPvorgColors
import com.pvorg.android.ui.theme.Radius
import com.pvorg.android.ui.theme.Spacing
import com.pvorg.android.ui.theme.tabular

private val LabelWidth = 300.dp
private val ColumnWidth = 84.dp
private val RowHeight = 56.dp
private val HeaderHeight = 48.dp

@Composable
fun MappingScreen(kind: MappingKind, state: UiState, viewModel: AppViewModel, actions: GlobalActions) {
    val library = state.library ?: return
    val session = state.session(kind)
    val rows = state.mappingRows[kind].orEmpty()
    val columns = library.columns(kind)
    val undecided = state.undecidedCounts[kind] ?: 0
    val subtitle = when {
        session.hasChanges -> Format.counted(session.changeCount, "unsaved change")
        undecided == 0 -> "All videos decided"
        else -> "${Format.counted(undecided, "video")} need decisions"
    }

    Column(Modifier.fillMaxSize()) {
        PageTopBar(
            title = kind.title,
            subtitle = subtitle,
            actions = {
                SingleChoiceSegmentedButtonRow(Modifier.padding(end = Spacing.sm)) {
                    listOf(false to "Undecided", true to "All").forEachIndexed { index, (showsDecided, label) ->
                        SegmentedButton(
                            selected = session.showsDecided == showsDecided,
                            onClick = { viewModel.setShowsDecided(kind, showsDecided) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            icon = {},
                            label = { Text(label) },
                        )
                    }
                }
                SearchField(session.query, { viewModel.setMappingQuery(kind, it) }, "Search videos", onEditingChange = { viewModel.isEditingText = it }, focusRequest = state.searchFocusRequest)
                ActionIcon(Icons.AutoMirrored.Outlined.Undo, "Revert unsaved changes", { viewModel.requestRevert(kind) }, enabled = session.hasChanges)
                Button(onClick = { viewModel.saveMappings(kind) }, enabled = session.hasChanges, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                    Text("Save")
                }
                OverflowMenu(actions)
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                columns.isEmpty() -> EmptyState(
                    if (kind == MappingKind.GALLERIES) Icons.Outlined.Collections else Icons.Outlined.Sell,
                    if (kind == MappingKind.GALLERIES) "No Galleries" else "No Tags",
                    if (kind == MappingKind.GALLERIES) "Add folders of images to the GAL folder of “${library.rootName}”, then reload the library."
                    else "Add tag names to the “tags” list in pvorg.json, then reload the library.",
                )
                library.videos.isEmpty() -> EmptyState(Icons.Outlined.Movie, "No Videos", "There’s nothing to map yet.")
                rows.isEmpty() && session.query.isNotBlank() -> EmptyState(
                    Icons.Outlined.SearchOff,
                    "No Results for “${session.query.trim()}”",
                    "Check the spelling or try a new search.",
                )
                rows.isEmpty() -> EmptyState(
                    Icons.Outlined.CheckCircle,
                    "All Caught Up",
                    "Every video has a decision for every ${kind.columnNoun}.",
                ) { OutlinedButton(onClick = { viewModel.setShowsDecided(kind, true) }) { Text("Show All Videos") } }
                else -> Matrix(kind, rows, columns, session, viewModel)
            }
        }
        if (columns.isNotEmpty() && library.videos.isNotEmpty()) StatusBar(session, shown = rows.size, total = library.videos.size)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Matrix(kind: MappingKind, rows: List<MappingRow>, columns: List<String>, session: MappingSession, viewModel: AppViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // With few columns the matrix still spans the page (spec §6.3).
        val contentWidth: Dp = maxOf(maxWidth, LabelWidth + ColumnWidth * columns.size)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(Modifier.width(contentWidth).fillMaxSize()) {
                stickyHeader(key = "header") { Header(kind, columns, viewModel) }
                itemsIndexed(rows, key = { _, row -> row.video.filename }) { index, row ->
                    MatrixRow(kind, row, index, columns, session, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Header(kind: MappingKind, columns: List<String>, viewModel: AppViewModel) {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Video",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(LabelWidth).padding(start = Spacing.lg),
            )
            columns.forEach { column ->
                var open by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .width(ColumnWidth)
                        .height(HeaderHeight)
                        .clickable(onClickLabel = "Set every shown video for $column") { open = true }
                        .padding(horizontal = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        column,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        Text(
                            "Set all shown videos to",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                        TriState.entries.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value.title) },
                                leadingIcon = { Icon(value.icon(), contentDescription = null) },
                                onClick = { open = false; viewModel.setColumn(kind, column, value) },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatrixRow(kind: MappingKind, row: MappingRow, index: Int, columns: List<String>, session: MappingSession, viewModel: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    val zebra = if (index % 2 == 1) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surface
    Row(Modifier.fillMaxWidth().height(RowHeight).background(zebra), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(LabelWidth)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Set every column")
                    .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Thumbnail(row.video.contactSheet, contentDescription = null, modifier = Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(Radius.small)))
                Column(Modifier.weight(1f)) {
                    Text(DisplayName.title(row.video), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        DisplayName.credits(row.video),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Text(
                    "Set every column to",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
                TriState.entries.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value.title) },
                        leadingIcon = { Icon(value.icon(), contentDescription = null) },
                        onClick = { menu = false; viewModel.setRow(kind, row, value) },
                    )
                }
            }
        }
        columns.forEach { column ->
            val value = session.state(row, column)
            val modified = session.isModified(row, column)
            Box(
                Modifier
                    .width(ColumnWidth)
                    .height(RowHeight)
                    .clickable(role = Role.Button, onClickLabel = "Change") { viewModel.cycleCell(kind, row, column) }
                    .semantics {
                        contentDescription = "${DisplayName.title(row.video)}, $column"
                        stateDescription = if (modified) "${value.title}, unsaved" else value.title
                    },
                contentAlignment = Alignment.Center,
            ) {
                TriStateBadge(value, modified = modified)
            }
        }
    }
}

@Composable
private fun StatusBar(session: MappingSession, shown: Int, total: Int) {
    Column {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            listOf(TriState.UNDECIDED, TriState.YES, TriState.NO).forEach { value ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                    TriStateBadge(value, width = 22, height = 18)
                    Text(value.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Tap a cell to cycle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.weight(1f))
            if (session.hasChanges) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                    Box(Modifier.size(7.dp).background(LocalPvorgColors.current.pending, CircleShape))
                    Text(
                        Format.counted(session.changeCount, "unsaved change"),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = LocalPvorgColors.current.pending,
                    )
                }
            }
            Text(
                "${Format.number(shown)} of ${Format.counted(total, "video")} shown",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
