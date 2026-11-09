package com.pvorg.android

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pvorg.android.qa.QaHooks
import com.pvorg.android.state.AppViewModel
import com.pvorg.android.ui.PvorgApp
import com.pvorg.android.ui.theme.PvorgTheme
import com.pvorg.android.ui.theme.isDark

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::onFolderPicked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start(QaHooks.libraryOverride(this, intent))
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val dark = isDark(appearance)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            PvorgTheme(dark) {
                PvorgApp(viewModel, onChooseFolder = { pickFolder.launch(null) })
            }
        }
        QaHooks.attach(this, viewModel)
    }

    /**
     * Hardware-keyboard shortcuts (spec §7.1). Typing always wins: while a
     * search field has focus, keys go to it. Dialogs have their own windows,
     * so their keys never reach here.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !viewModel.isEditingText) {
            val ctrl = event.isCtrlPressed || event.isMetaPressed
            if (viewModel.onKey(event.keyCode, ctrl, event.isShiftPressed, event.isAltPressed)) return true
        }
        return super.dispatchKeyEvent(event)
    }
}
