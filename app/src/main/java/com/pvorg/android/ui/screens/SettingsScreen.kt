package com.pvorg.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pvorg.android.state.Appearance
import com.pvorg.android.state.AppViewModel
import com.pvorg.android.state.UiState
import com.pvorg.android.ui.GlobalActions
import com.pvorg.android.ui.components.PageTopBar
import com.pvorg.android.ui.theme.Radius
import com.pvorg.android.ui.theme.Spacing

@Composable
fun SettingsScreen(state: UiState, viewModel: AppViewModel, actions: GlobalActions) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        PageTopBar(title = "Settings", subtitle = null)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            SettingsGroup("Appearance", footer = "The viewer always uses a dark stage so images and video look their best.") {
                Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Text("Appearance", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        Appearance.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = appearance == option,
                                onClick = { viewModel.setAppearance(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size),
                                label = { Text(option.title) },
                            )
                        }
                    }
                }
            }
            SettingsGroup("Library") {
                Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Library folder", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            state.locationDescription ?: state.library?.rootName.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(onClick = actions.onChooseFolder) { Text("Change Folder…") }
                }
                HorizontalDivider(Modifier.padding(start = Spacing.lg))
                PathRow("Configuration", "pvorg.json")
                HorizontalDivider(Modifier.padding(start = Spacing.lg))
                PathRow("Videos", "VID (contact sheets in VID/img)")
                HorizontalDivider(Modifier.padding(start = Spacing.lg))
                PathRow("Galleries", "GAL (one folder per gallery)")
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, footer: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.lg).semantics { heading() },
        )
        Surface(shape = RoundedCornerShape(Radius.large), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column { content() }
        }
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.lg),
            )
        }
    }
}

@Composable
private fun PathRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
