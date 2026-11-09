package com.pvorg.android.qa

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import com.pvorg.android.data.LibraryStorage
import com.pvorg.android.state.AppViewModel

/** Debug QA hooks; none yet. */
object QaHooks {
    @Suppress("UNUSED_PARAMETER")
    fun libraryOverride(activity: Activity, intent: Intent): LibraryStorage? = null

    @Suppress("UNUSED_PARAMETER")
    fun attach(activity: ComponentActivity, viewModel: AppViewModel) = Unit
}
