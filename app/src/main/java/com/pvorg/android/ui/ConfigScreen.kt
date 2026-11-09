package com.pvorg.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConfigScreen(
    allGalleries: List<String>,
    allTags: List<String>,
    selectedGalleries: Set<String>,
    selectedTags: Set<String>,
    gallerySelectionDefaultsToAll: Boolean,
    tagSelectionDefaultsToAll: Boolean,
    onToggleGallery: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onUseAllGalleries: () -> Unit,
    onUseAllTags: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FilterSection(
            label = "Galleries",
            items = allGalleries,
            selected = selectedGalleries,
            emptyMeansAll = gallerySelectionDefaultsToAll,
            onToggle = onToggleGallery,
            onUseAll = onUseAllGalleries,
        )
        FilterSection(
            label = "Tags",
            items = allTags,
            selected = selectedTags,
            emptyMeansAll = tagSelectionDefaultsToAll,
            onToggle = onToggleTag,
            onUseAll = onUseAllTags,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    label: String,
    items: List<String>,
    selected: Set<String>,
    emptyMeansAll: Boolean,
    onToggle: (String) -> Unit,
    onUseAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = when {
                        items.isEmpty() -> "No $label available."
                        emptyMeansAll && selected.isEmpty() -> "Using all $label."
                        else -> "${selected.size} of ${items.size} selected."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onUseAll,
                enabled = items.isNotEmpty(),
            ) {
                Text("Use All")
            }
        }
        if (items.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    FilterChip(
                        selected = item in selected,
                        onClick = { onToggle(item) },
                        label = { Text(item) },
                    )
                }
            }
        }
    }
}
