package com.pvorg.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.media3.exoplayer.ExoPlayer
import com.pvorg.android.AppPhase
import com.pvorg.android.MainUiState
import com.pvorg.android.model.AppTab
import com.pvorg.android.model.MappingPageData
import com.pvorg.android.model.TriState

@Composable
fun PvorgApp(
    uiState: MainUiState,
    player: ExoPlayer,
    onRetryDirectorySelection: () -> Unit,
    onExit: () -> Unit,
    onTabSelected: (AppTab) -> Unit,
    onChangeFolder: () -> Unit,
    onToggleGalleryFilter: (String) -> Unit,
    onToggleTagFilter: (String) -> Unit,
    onUseAllGalleries: () -> Unit,
    onUseAllTags: () -> Unit,
    onPreviousCombination: () -> Unit,
    onNextCombination: () -> Unit,
    onToggleViewerMode: () -> Unit,
    onSaveGalleryMappings: suspend (Map<String, Map<String, TriState>>) -> Result<Unit>,
    onSaveTagMappings: suspend (Map<String, Map<String, TriState>>) -> Result<Unit>,
    galleryMappingPage: (Int, Int, Boolean) -> MappingPageData,
    tagMappingPage: (Int, Int, Boolean) -> MappingPageData,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (uiState.phase) {
            AppPhase.REQUESTING_DIRECTORY -> LoadingState(message = "Opening folder picker...")
            AppPhase.LOADING -> LoadingState(message = uiState.statusMessage)
            AppPhase.ERROR -> ErrorState(
                message = uiState.errorMessage ?: "Unknown error",
                onRetry = onRetryDirectorySelection,
                onExit = onExit,
            )
            AppPhase.READY -> ReadyState(
                uiState = uiState,
                player = player,
                onTabSelected = onTabSelected,
                onChangeFolder = onChangeFolder,
                onToggleGalleryFilter = onToggleGalleryFilter,
                onToggleTagFilter = onToggleTagFilter,
                onUseAllGalleries = onUseAllGalleries,
                onUseAllTags = onUseAllTags,
                onPreviousCombination = onPreviousCombination,
                onNextCombination = onNextCombination,
                onToggleViewerMode = onToggleViewerMode,
                onSaveGalleryMappings = onSaveGalleryMappings,
                onSaveTagMappings = onSaveTagMappings,
                galleryMappingPage = galleryMappingPage,
                tagMappingPage = tagMappingPage,
            )
        }
    }
}

@Composable
private fun ReadyState(
    uiState: MainUiState,
    player: ExoPlayer,
    onTabSelected: (AppTab) -> Unit,
    onChangeFolder: () -> Unit,
    onToggleGalleryFilter: (String) -> Unit,
    onToggleTagFilter: (String) -> Unit,
    onUseAllGalleries: () -> Unit,
    onUseAllTags: () -> Unit,
    onPreviousCombination: () -> Unit,
    onNextCombination: () -> Unit,
    onToggleViewerMode: () -> Unit,
    onSaveGalleryMappings: suspend (Map<String, Map<String, TriState>>) -> Result<Unit>,
    onSaveTagMappings: suspend (Map<String, Map<String, TriState>>) -> Result<Unit>,
    galleryMappingPage: (Int, Int, Boolean) -> MappingPageData,
    tagMappingPage: (Int, Int, Boolean) -> MappingPageData,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = uiState.currentTab.ordinal) {
            AppTab.entries.forEach { tab ->
                Tab(
                    selected = tab == uiState.currentTab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = uiState.selectedRoot?.rootName ?: "No folder selected",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${uiState.videos.size} videos · ${uiState.galleries.size} galleries",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onChangeFolder) {
                Text("Change Folder")
            }
        }

        HorizontalDivider()

        when (uiState.currentTab) {
            AppTab.HOME -> HomeScreen(
                uiState = uiState,
                player = player,
                onPreviousCombination = onPreviousCombination,
                onNextCombination = onNextCombination,
                onToggleViewerMode = onToggleViewerMode,
            )
            AppTab.CONFIG -> ConfigScreen(
                allGalleries = uiState.allGalleryNames,
                allTags = uiState.allTags,
                selectedGalleries = uiState.selectedGalleries,
                selectedTags = uiState.selectedTags,
                gallerySelectionDefaultsToAll = true,
                tagSelectionDefaultsToAll = true,
                onToggleGallery = onToggleGalleryFilter,
                onToggleTag = onToggleTagFilter,
                onUseAllGalleries = onUseAllGalleries,
                onUseAllTags = onUseAllTags,
            )
            AppTab.MAPPER -> MappingScreen(
                title = "Video-Gallery Mapper",
                sourceVersion = uiState.config.hashCode() + uiState.videos.size + uiState.galleries.size,
                fetchPage = galleryMappingPage,
                onSave = onSaveGalleryMappings,
            )
            AppTab.TAG_MAPPER -> MappingScreen(
                title = "Video-Tag Mapper",
                sourceVersion = uiState.config.hashCode() + uiState.videos.size + uiState.allTags.size,
                fetchPage = tagMappingPage,
                onSave = onSaveTagMappings,
            )
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Unable to load PVORG",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
                Button(onClick = onExit) {
                    Text("Exit")
                }
            }
        }
    }
}
