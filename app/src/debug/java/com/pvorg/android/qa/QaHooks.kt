package com.pvorg.android.qa

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import com.pvorg.android.data.FileLibraryStorage
import com.pvorg.android.data.LibraryStorage
import com.pvorg.android.state.AppViewModel
import java.io.File

/**
 * Debug-only QA hooks (absent from release builds):
 *  - `--es pvorg.qa.library <path>` opens a library folder from device storage
 *    without the folder picker;
 *  - `--ez pvorg.qa.tour true` runs the screenshot and keyboard tour driven by
 *    `scripts/qa-tour.sh` on the host.
 */
object QaHooks {
    const val EXTRA_LIBRARY = "pvorg.qa.library"
    const val EXTRA_TOUR = "pvorg.qa.tour"

    fun libraryOverride(activity: Activity, intent: Intent): LibraryStorage? {
        val path = intent.getStringExtra(EXTRA_LIBRARY) ?: return null
        return FileLibraryStorage(File(path))
    }

    fun attach(activity: ComponentActivity, viewModel: AppViewModel) {
        if (activity.intent.getBooleanExtra(EXTRA_TOUR, false)) Tour(activity, viewModel).start()
    }
}
