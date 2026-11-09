package com.pvorg.android

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pvorg.android.ui.PvorgApp
import com.pvorg.android.ui.theme.PvorgTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val shortcut = event.toShortcutKey()
            if (shortcut != null && viewModel.onShortcut(shortcut)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PvorgTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val activity = LocalContext.current as Activity
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                    onResult = { uri ->
                        if (uri != null) {
                            viewModel.onRootSelected(uri)
                        } else {
                            viewModel.onRootSelectionCancelled()
                        }
                    },
                )

                LaunchedEffect(uiState.pickerRequestToken, uiState.phase) {
                    if (uiState.phase == AppPhase.REQUESTING_DIRECTORY) {
                        launcher.launch(null)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    PvorgApp(
                        uiState = uiState,
                        player = viewModel.player,
                        onRetryDirectorySelection = viewModel::requestDirectorySelection,
                        onExit = { activity.finish() },
                        onTabSelected = viewModel::setTab,
                        onChangeFolder = viewModel::requestDirectorySelection,
                        onToggleGalleryFilter = viewModel::toggleGalleryFilter,
                        onToggleTagFilter = viewModel::toggleTagFilter,
                        onUseAllGalleries = viewModel::useAllGalleries,
                        onUseAllTags = viewModel::useAllTags,
                        onPreviousCombination = viewModel::previousCombination,
                        onNextCombination = viewModel::nextCombination,
                        onToggleViewerMode = viewModel::toggleViewerMode,
                        onSaveGalleryMappings = viewModel::saveGalleryMappings,
                        onSaveTagMappings = viewModel::saveTagMappings,
                        galleryMappingPage = viewModel::galleryMappingPage,
                        tagMappingPage = viewModel::tagMappingPage,
                    )
                }
            }
        }
    }
}

private fun KeyEvent.toShortcutKey(): ShortcutKey? {
    return when (keyCode) {
        KeyEvent.KEYCODE_V -> ShortcutKey.HOME
        KeyEvent.KEYCODE_C -> ShortcutKey.CONFIG
        KeyEvent.KEYCODE_P -> ShortcutKey.TOGGLE_VIEWER_MODE
        KeyEvent.KEYCODE_B -> ShortcutKey.PREVIOUS_COMBINATION
        KeyEvent.KEYCODE_N -> ShortcutKey.NEXT_COMBINATION
        KeyEvent.KEYCODE_SPACE -> ShortcutKey.PLAY_PAUSE
        KeyEvent.KEYCODE_DPAD_LEFT -> ShortcutKey.SEEK_BACK
        KeyEvent.KEYCODE_DPAD_RIGHT -> ShortcutKey.SEEK_FORWARD
        KeyEvent.KEYCODE_0 -> ShortcutKey.SEEK_PERCENT(0.0)
        KeyEvent.KEYCODE_1 -> ShortcutKey.SEEK_PERCENT(0.1)
        KeyEvent.KEYCODE_2 -> ShortcutKey.SEEK_PERCENT(0.2)
        KeyEvent.KEYCODE_3 -> ShortcutKey.SEEK_PERCENT(0.3)
        KeyEvent.KEYCODE_4 -> ShortcutKey.SEEK_PERCENT(0.4)
        KeyEvent.KEYCODE_5 -> ShortcutKey.SEEK_PERCENT(0.5)
        KeyEvent.KEYCODE_6 -> ShortcutKey.SEEK_PERCENT(0.6)
        KeyEvent.KEYCODE_7 -> ShortcutKey.SEEK_PERCENT(0.7)
        KeyEvent.KEYCODE_8 -> ShortcutKey.SEEK_PERCENT(0.8)
        KeyEvent.KEYCODE_9 -> ShortcutKey.SEEK_PERCENT(0.9)
        else -> null
    }
}
