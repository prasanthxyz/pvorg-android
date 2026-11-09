package com.pvorg.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pvorg.android.domain.DisplayName
import com.pvorg.android.domain.Format
import com.pvorg.android.domain.SkippedFile
import com.pvorg.android.domain.StaleCleanupPreview
import com.pvorg.android.state.UiState
import com.pvorg.android.ui.theme.Spacing
import com.pvorg.android.ui.theme.tabular

/** Spec §6.2: names up to six videos; Cancel is the default (it takes initial focus). */
@Composable
fun ConfirmDeleteDialog(state: UiState, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val filenames = state.pendingDeletion
    // Named in Library order, as the user sees them.
    val selected = filenames.toSet()
    val videos = state.library?.videos.orEmpty().filter { it.filename in selected }
    val title = if (videos.size == 1) "Delete “${DisplayName.title(videos[0])}”?" else "Delete ${Format.counted(filenames.size, "Video")}?"
    val consequence = "The video files, their contact sheets, mapping decisions and cached metadata will be permanently removed. This can’t be undone."
    val names = videos.takeIf { it.size in 2..6 }?.let { DisplayName.joinList(it.map(DisplayName::title)) + ".\n\n" }.orEmpty()
    val cancelFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(names + consequence) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(title.removeSuffix("?"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) { Text("Cancel") }
        },
    )
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}

/** Spec §3.5 / §6.4: the destructive action is never the default, especially in the high-risk case. */
@Composable
fun StaleCleanupDialog(preview: StaleCleanupPreview, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val nothing = preview.staleEntries.isEmpty()
    val cancelFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(if (nothing) Icons.Outlined.CheckCircle else Icons.Outlined.CleaningServices, contentDescription = null) },
        title = { Text(if (nothing) "No Stale Entries" else "Clean Up Stale Entries") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    if (nothing) "Every entry in pvorg.json matches a video in your videos folder."
                    else "These pvorg.json entries refer to videos that are no longer in your videos folder. Removing them doesn’t touch any files.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SummaryRow("Videos folder", preview.videosFolder)
                SummaryRow("Videos on disk", Format.number(preview.videoFileCount))
                SummaryRow("Entries in pvorg.json", Format.number(preview.totalRelationCount))
                SummaryRow("Stale entries", Format.number(preview.staleEntries.size))
                if (preview.isHighRisk) {
                    Callout(
                        CalloutTone.CRITICAL,
                        "No videos are visible in the videos folder",
                        "If a drive is disconnected or the folder is temporarily unavailable, removing these entries permanently deletes your mapping decisions.",
                    )
                }
                if (!nothing) {
                    Text("Entries to Remove", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                        items(preview.staleEntries) { entry ->
                            Text(entry, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = Spacing.xs))
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!nothing) {
                TextButton(onClick = onConfirm) {
                    Text("Remove ${Format.counted(preview.staleEntries.size, "Entry", "Entries")}", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) { Text(if (nothing) "Done" else "Cancel") }
        },
    )
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(180.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Lists files skipped for breaking the naming convention, with the reasons (spec §6.2). */
@Composable
fun SkippedFilesDialog(files: List<SkippedFile>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skipped Files") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                items(files) { file ->
                    Column {
                        Text(file.filename, style = MaterialTheme.typography.bodyLarge)
                        Text(file.reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
