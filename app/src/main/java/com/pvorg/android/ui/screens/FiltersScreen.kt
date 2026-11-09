package com.pvorg.android.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pvorg.android.domain.DisplayName
import com.pvorg.android.domain.Format
import com.pvorg.android.domain.Search
import com.pvorg.android.domain.VideoSort
import com.pvorg.android.state.AppViewModel
import com.pvorg.android.state.FilterSection
import com.pvorg.android.state.Page
import com.pvorg.android.state.UiState
import com.pvorg.android.ui.GlobalActions
import com.pvorg.android.ui.OverflowMenu
import com.pvorg.android.ui.components.ActionIcon
import com.pvorg.android.ui.components.FilterToken
import com.pvorg.android.ui.components.PageTopBar
import com.pvorg.android.ui.components.SearchField
import com.pvorg.android.ui.components.Thumbnail
import com.pvorg.android.ui.theme.Radius
import com.pvorg.android.ui.theme.Spacing
import com.pvorg.android.ui.theme.tabular

private val FilterSection.explanation: String
    get() = when (this) {
        FilterSection.GALLERIES -> "Videos are paired only with the galleries you include."
        FilterSection.STUDIOS -> "Only videos from the studios you include are shown."
        FilterSection.TAGS -> "Only videos marked Yes for every selected tag in Tag Mapping are shown."
        FilterSection.CAST -> "Only videos featuring everyone selected are shown."
    }

private val FilterSection.emptyMessage: String
    get() = when (this) {
        FilterSection.GALLERIES -> "No gallery folders were found in GAL."
        FilterSection.STUDIOS -> "No videos were found."
        FilterSection.TAGS -> "No tags are defined in pvorg.json."
        FilterSection.CAST -> "No cast names were found in video filenames."
    }

/** Gallery and tag names are shown as written; studio and cast are humanized (spec §2). */
private fun FilterSection.label(value: String): String = when (this) {
    FilterSection.STUDIOS, FilterSection.CAST -> DisplayName.humanize(value)
    else -> value
}

@Composable
fun FiltersScreen(state: UiState, viewModel: AppViewModel, actions: GlobalActions) {
    val library = state.library ?: return
    Column(Modifier.fillMaxSize()) {
        PageTopBar(
            title = "Filters",
            subtitle = Format.counted(state.combinations.size, "combination"),
            actions = {
                SearchField(state.filterQuery, viewModel::setFilterQuery, "Filter names", onEditingChange = { viewModel.isEditingText = it }, focusRequest = state.searchFocusRequest)
                ActionIcon(Icons.Outlined.RestartAlt, "Reset Filters", viewModel::resetFilters, enabled = !state.filters.isDefault(library))
                ActionIcon(
                    Icons.AutoMirrored.Outlined.ViewSidebar,
                    if (state.showsPreviewPanel) "Hide Preview" else "Show Preview",
                    viewModel::togglePreviewPanel,
                )
                OverflowMenu(actions)
            },
        )
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
            ) {
                items(FilterSection.entries, key = { it.name }) { section ->
                    FilterSectionView(section, state, viewModel)
                }
            }
            if (state.showsPreviewPanel) {
                VerticalDivider()
                PreviewPanel(state, viewModel, Modifier.width(320.dp).fillMaxHeight())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSectionView(section: FilterSection, state: UiState, viewModel: AppViewModel) {
    val all = viewModel.allValues(section)
    val selected = when (section) {
        FilterSection.GALLERIES -> state.filters.galleries
        FilterSection.STUDIOS -> state.filters.studios
        FilterSection.TAGS -> state.filters.tags
        FilterSection.CAST -> state.filters.cast
    }
    val items = remember(all) { VideoSort.sortedLabels(all.toList()) { section.label(it) } }
    val visible = if (state.filterQuery.isBlank()) items else items.filter { Search.matches(section.label(it), state.filterQuery) }
    val summary = if (section.requires) {
        if (selected.isEmpty()) "Any" else "${Format.number(selected.size)} required"
    } else {
        if (selected.size == all.size) "All ${Format.number(all.size)}" else "${Format.number(selected.size)} of ${Format.number(all.size)}"
    }

    Column(Modifier.widthIn(max = 1000.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(summary, style = MaterialTheme.typography.bodyLarge.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(section.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (all.isNotEmpty()) {
                if (section.requires) {
                    TextButton(onClick = { viewModel.includeNone(section) }, enabled = selected.isNotEmpty()) { Text("Clear") }
                } else {
                    TextButton(onClick = { viewModel.includeAll(section) }, enabled = selected.size < all.size) { Text("Include All") }
                    TextButton(onClick = { viewModel.includeNone(section) }, enabled = selected.isNotEmpty()) { Text("Include None") }
                }
            }
        }
        when {
            all.isEmpty() -> Text(section.emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            visible.isEmpty() -> Text("No matches", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                visible.forEach { value -> ChipWithMenu(section, value, value in selected, viewModel) }
            }
        }
    }
}

@Composable
private fun ChipWithMenu(section: FilterSection, value: String, selected: Boolean, viewModel: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    val label = section.label(value)
    Box {
        FilterToken(label, selected, onToggle = { viewModel.toggleFilter(section, value) }, onLongPress = { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (section.requires) {
                DropdownMenuItem(text = { Text("Require Only “$label”") }, onClick = { menu = false; viewModel.only(section, value) })
            } else {
                DropdownMenuItem(text = { Text("Include Only “$label”") }, onClick = { menu = false; viewModel.only(section, value) })
                DropdownMenuItem(text = { Text("Include All Except “$label”") }, onClick = { menu = false; viewModel.allExcept(section, value) })
            }
        }
    }
}

/** Count, breakdown, Open Viewer and Up Next from the viewer's position (spec §6.1). */
@Composable
private fun PreviewPanel(state: UiState, viewModel: AppViewModel, modifier: Modifier) {
    val combinations = state.combinations
    val upNext = if (combinations.isEmpty()) emptyList() else (0 until minOf(10, combinations.size)).map { offset ->
        val index = (state.combinationIndex + offset) % combinations.size
        index to combinations[index]
    }
    val videos = combinations.map { it.video.filename }.toSet().size
    val galleries = combinations.map { it.gallery.name }.toSet().size
    LazyColumn(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item {
            Text("Combinations", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.number(combinations.size), style = MaterialTheme.typography.displaySmall.tabular())
            Text(
                "${Format.counted(videos, "video")} · ${Format.counted(galleries, "gallery", "galleries")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            Button(onClick = { viewModel.showPage(Page.VIEWER) }, enabled = combinations.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Open Viewer")
            }
            Spacer(Modifier.height(Spacing.lg))
            Text("Up Next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (combinations.isEmpty()) {
                Text("Nothing matches the current filters.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
            }
        }
        items(upNext, key = { it.second.id }) { (index, combination) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.medium))
                    .clickable(onClickLabel = "Show in Viewer") { viewModel.showCombination(index) }
                    .padding(vertical = Spacing.xs + 2.dp, horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Thumbnail(
                    combination.video.contactSheet,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp, 40.dp).clip(RoundedCornerShape(Radius.small)),
                )
                Column(Modifier.weight(1f)) {
                    Text(DisplayName.title(combination.video), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(combination.gallery.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
