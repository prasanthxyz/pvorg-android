package com.pvorg.android.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pvorg.android.model.MappingPageData
import com.pvorg.android.model.MappingVideoRow
import com.pvorg.android.model.TriState
import kotlinx.coroutines.launch

@Composable
fun MappingScreen(
    title: String,
    sourceVersion: Int,
    fetchPage: (Int, Int, Boolean) -> MappingPageData,
    onSave: suspend (Map<String, Map<String, TriState>>) -> Result<Unit>,
) {
    val pageSize = 20
    var page by remember(sourceVersion) { mutableIntStateOf(1) }
    var hideDecided by remember(sourceVersion) { mutableStateOf(true) }
    var refreshKey by remember(sourceVersion) { mutableIntStateOf(0) }
    var data by remember(sourceVersion) { mutableStateOf(MappingPageData.empty(pageSize)) }
    var localChanges by remember(sourceVersion) { mutableStateOf<Map<String, Map<String, TriState>>>(emptyMap()) }
    var feedback by remember(sourceVersion) { mutableStateOf<String?>(null) }
    var previewUri by remember(sourceVersion) { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(page, hideDecided, refreshKey, sourceVersion) {
        val reloaded = fetchPage(page, pageSize, hideDecided)
        data = reloaded
        if (page != reloaded.page) {
            page = reloaded.page
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hideDecided,
                    onCheckedChange = {
                        hideDecided = it
                        page = 1
                        localChanges = emptyMap()
                    },
                )
                Text("Hide fully decided")
            }
            if (localChanges.isNotEmpty()) {
                Text("${localChanges.size} unsaved")
            }
            Button(
                enabled = localChanges.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val result = onSave(localChanges)
                        feedback = result.exceptionOrNull()?.message ?: "Saved"
                        if (result.isSuccess) {
                            localChanges = emptyMap()
                            refreshKey += 1
                        }
                    }
                },
            ) {
                Text("Save")
            }
        }

        if (feedback != null) {
            Text(
                text = feedback.orEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                data.columns.isEmpty() -> EmptyState("No columns to map.")
                data.videos.isEmpty() -> EmptyState(
                    if (hideDecided) "All videos are fully decided." else "No videos found."
                )
                else -> MappingTable(
                    data = data,
                    localChanges = localChanges,
                    onPreview = { previewUri = it },
                    onStateChange = { filename, column, newState, original ->
                        val updated = localChanges.toMutableMap()
                        val entry = updated[filename]?.toMutableMap() ?: mutableMapOf()
                        if (newState == original) {
                            entry.remove(column)
                        } else {
                            entry[column] = newState
                        }
                        if (entry.isEmpty()) {
                            updated.remove(filename)
                        } else {
                            updated[filename] = entry
                        }
                        localChanges = updated
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = data.page > 1,
                onClick = { page = maxOf(1, page - 1) },
            ) {
                Text("Previous")
            }
            Text("Page ${data.page} · ${data.total} videos")
            Button(
                enabled = data.page * data.pageSize < data.total,
                onClick = { page += 1 },
            ) {
                Text("Next")
            }
        }
    }

    if (previewUri != null) {
        AlertDialog(
            onDismissRequest = { previewUri = null },
            confirmButton = {
                Button(onClick = { previewUri = null }) {
                    Text("Close")
                }
            },
            text = {
                AsyncImage(
                    model = previewUri,
                    contentDescription = "Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    contentScale = ContentScale.Fit,
                )
            },
        )
    }
}

@Composable
private fun MappingTable(
    data: MappingPageData,
    localChanges: Map<String, Map<String, TriState>>,
    onPreview: (Uri) -> Unit,
    onStateChange: (filename: String, column: String, newState: TriState, original: TriState) -> Unit,
) {
    val horizontalState = rememberScrollState()
    val verticalState = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .horizontalScroll(horizontalState)
            .verticalScroll(verticalState),
    ) {
        HeaderRow(columns = data.columns)
        data.videos.forEach { row ->
            MappingRow(
                row = row,
                columns = data.columns,
                localChanges = localChanges[row.filename].orEmpty(),
                onPreview = onPreview,
                onStateChange = onStateChange,
            )
        }
    }
}

@Composable
private fun HeaderRow(columns: List<String>) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TGP", modifier = Modifier.width(110.dp))
        Text("Video", modifier = Modifier.width(240.dp))
        columns.forEach { column ->
            Text(
                text = column,
                modifier = Modifier.width(96.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MappingRow(
    row: MappingVideoRow,
    columns: List<String>,
    localChanges: Map<String, TriState>,
    onPreview: (Uri) -> Unit,
    onStateChange: (filename: String, column: String, newState: TriState, original: TriState) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = row.tgpUri,
            contentDescription = row.filename,
            modifier = Modifier
                .width(96.dp)
                .height(54.dp)
                .clickable { onPreview(row.tgpUri) },
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = row.title,
            modifier = Modifier.width(240.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        columns.forEach { column ->
            val original = row.map[column] ?: TriState.UNDECIDED
            val current = localChanges[column] ?: original
            TriStateChip(
                state = current,
                isModified = column in localChanges,
                onClick = {
                    onStateChange(row.filename, column, current.next, original)
                },
            )
        }
    }
}

@Composable
private fun TriStateChip(
    state: TriState,
    isModified: Boolean,
    onClick: () -> Unit,
) {
    val background = when (state) {
        TriState.YES -> Color(0xFF2E7D32)
        TriState.NO -> Color(0xFF8E1B1B)
        TriState.UNDECIDED -> Color(0xFF444444)
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(88.dp)
            .background(
                color = background.copy(alpha = if (isModified) 1f else 0.82f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (state) {
                TriState.YES -> "YES"
                TriState.NO -> "NO"
                TriState.UNDECIDED -> "?"
            },
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
