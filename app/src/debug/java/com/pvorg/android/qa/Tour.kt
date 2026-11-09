package com.pvorg.android.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pvorg.android.domain.MappingKind
import com.pvorg.android.state.AppViewModel
import com.pvorg.android.state.Appearance
import com.pvorg.android.state.FilterSection
import com.pvorg.android.state.Page
import com.pvorg.android.state.Phase
import com.pvorg.android.state.ViewerMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

/**
 * Walks every page and state, asking the host (`scripts/qa-tour.sh`) for a
 * screenshot or a key press via logcat lines tagged `PVORG_TOUR`. The host
 * acknowledges each request with a broadcast, so nothing relies on timing.
 */
class Tour(private val activity: ComponentActivity, private val vm: AppViewModel) {
    private var ack = CompletableDeferred<Unit>()
    private val state get() = vm.state.value

    fun start() {
        ContextCompat.registerReceiver(
            activity,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    ack.complete(Unit)
                }
            },
            IntentFilter(ACK_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        activity.lifecycleScope.launch { run() }
    }

    private suspend fun run() {
        log("start")
        waitUntil { state.phase == Phase.Ready || state.phase is Phase.Failed }
        if (state.phase is Phase.Failed) {
            shot("startup-error")
            log("done")
            return
        }
        vm.setAppearance(Appearance.DARK)
        delay(800)
        keyboardChecks()
        for (appearance in listOf(Appearance.DARK, Appearance.LIGHT)) {
            vm.setAppearance(appearance)
            screens(appearance.name.lowercase())
        }
        vm.setAppearance(Appearance.DARK)
        vm.showPage(Page.VIEWER)
        log("done")
    }

    private suspend fun screens(p: String) {
        vm.showPage(Page.VIEWER)
        vm.showCombination(0)
        shot("$p-viewer-contact-sheet", settle = 2_000)

        vm.setViewerMode(ViewerMode.VIDEO)
        delay(2_000)
        vm.playerController.pause()
        vm.playerController.seekToFraction(0.4f)
        shot("$p-viewer-video")

        indexOf { it.video.filename.endsWith(".wmv") }?.let {
            vm.showCombination(it)
            vm.setViewerMode(ViewerMode.VIDEO)
            shot("$p-viewer-unplayable", settle = 2_000)
        }
        indexOf { it.video.contactSheet == null }?.let {
            vm.showCombination(it)
            shot("$p-viewer-no-contact-sheet")
        }
        indexOf { it.gallery.images.isEmpty() }?.let {
            vm.showCombination(it)
            shot("$p-viewer-empty-gallery")
        }

        vm.showCombination(0)
        delay(600)
        vm.excludeCurrentPairing()
        shot("$p-viewer-excluded-toast", settle = 1_200)
        vm.undo()
        delay(800)

        vm.includeNone(FilterSection.STUDIOS)
        shot("$p-viewer-no-matches")
        vm.resetFilters()

        vm.showPage(Page.FILTERS)
        shot("$p-filters")
        state.library?.galleryNames?.firstOrNull()?.let { vm.toggleFilter(FilterSection.GALLERIES, it) }
        vm.toggleFilter(FilterSection.TAGS, "outdoor")
        shot("$p-filters-narrowed")
        vm.resetFilters()

        vm.showPage(Page.LIBRARY)
        shot("$p-library", settle = 1_500)
        val first = state.libraryVisible.take(3).map { it.filename }
        first.firstOrNull()?.let(vm::startSelection)
        first.drop(1).forEach(vm::toggleSelection)
        shot("$p-library-selection")
        vm.requestDelete(state.selection)
        shot("$p-library-delete-confirm")
        vm.cancelDelete()
        vm.clearSelection()
        vm.setLibraryQuery("acme")
        shot("$p-library-search")
        vm.setLibraryQuery("zzzz")
        shot("$p-library-no-results")
        vm.setLibraryQuery("")
        vm.showPreview(state.libraryVisible.firstOrNull()?.filename)
        shot("$p-library-preview")
        vm.showPreview(null)
        vm.showSkippedFiles(true)
        shot("$p-library-skipped-files")
        vm.showSkippedFiles(false)

        vm.showPage(Page.GALLERY_MAPPING)
        shot("$p-gallery-mapping")
        val rows = state.mappingRows[MappingKind.GALLERIES].orEmpty().take(3)
        val columns = state.library?.columns(MappingKind.GALLERIES).orEmpty().take(2)
        rows.forEach { row -> columns.forEach { vm.cycleCell(MappingKind.GALLERIES, row, it) } }
        shot("$p-gallery-mapping-changes")
        vm.setShowsDecided(MappingKind.GALLERIES, true)
        shot("$p-gallery-mapping-all")
        vm.requestRevert(MappingKind.GALLERIES)
        shot("$p-gallery-mapping-revert")
        vm.confirmRevert()
        vm.setShowsDecided(MappingKind.GALLERIES, false)

        vm.showPage(Page.TAG_MAPPING)
        shot("$p-tag-mapping")

        vm.showPage(Page.VIEWER)
        vm.requestStaleCleanup()
        shot("$p-stale-cleanup", settle = 1_200)
        vm.dismissStaleCleanup()

        vm.showPage(Page.SETTINGS)
        shot("$p-settings")

        vm.setNavigationExpanded(false)
        vm.showPage(Page.VIEWER)
        shot("$p-viewer-rail", settle = 1_500)
        vm.setNavigationExpanded(true)
    }

    /** Real key presses injected by the host through the input system (spec §7.1). */
    private suspend fun keyboardChecks() {
        vm.showPage(Page.LIBRARY)
        delay(600)
        key("KEYCODE_C")
        check(state.page == Page.FILTERS, "C opens Filters")
        key("KEYCODE_V")
        check(state.page == Page.VIEWER, "V opens the viewer")

        val before = state.combinationIndex
        key("KEYCODE_N")
        check(state.combinationIndex == (before + 1) % state.combinations.size, "N advances to the next combination")
        key("KEYCODE_B")
        check(state.combinationIndex == before, "B returns to the previous combination")

        indexOf { it.video.filename.endsWith(".mp4") }?.let(vm::showCombination)
        delay(500)
        key("KEYCODE_P")
        delay(1_500)
        check(state.viewerMode == ViewerMode.VIDEO && vm.playerController.state.value.isPlaying, "P plays the video")
        key("KEYCODE_SPACE")
        check(!vm.playerController.state.value.isPlaying, "Space pauses")
        key("KEYCODE_5")
        val playback = vm.playerController.state.value
        check(abs(playback.positionMs - playback.durationMs / 2) < 600, "5 jumps to 50% (${playback.positionMs} of ${playback.durationMs} ms)")
        key("KEYCODE_DPAD_LEFT")
        check(vm.playerController.state.value.positionMs < playback.positionMs - 500, "← skips back")
        key("KEYCODE_P")
        check(state.viewerMode == ViewerMode.CONTACT_SHEET, "P returns to the contact sheet")

        // Exclude Pairing, undo and redo through the keyboard; pvorg.json must end up unchanged.
        val configFile = (activity.intent.getStringExtra(QaHooks.EXTRA_LIBRARY))?.let { File(it, "pvorg.json") }
        val original = configFile?.readText()
        vm.showCombination(0)
        delay(500)
        val target = state.currentCombination
        val count = state.combinations.size
        key("KEYCODE_DEL")
        val excluded = configFile?.readText()?.contains("\"${target?.gallery?.name}\"") == true
        check(state.combinations.size == count - 1 && state.combinationIndex == 0 && state.currentCombination?.id != target?.id, "⌫ removes the pairing and shows the next one in place")
        check(excluded && state.library?.config?.videoRelations?.get(target?.video?.filename)?.noGalleries?.contains(target?.gallery?.name) == true, "⌫ writes the gallery to excludedGalleries in pvorg.json")
        key("CTRL+KEYCODE_Z")
        check(state.combinations.size == count && state.currentCombination?.id == target?.id, "Ctrl+Z restores the pairing in place")
        key("CTRL+SHIFT+KEYCODE_Z")
        check(state.combinations.size == count - 1, "Ctrl+Shift+Z excludes it again")
        key("CTRL+KEYCODE_Z")
        check(configFile?.readText() == original, "pvorg.json is byte-identical after exclude, undo, redo, undo")

        // Typing always wins: shortcut letters typed into search reach the field.
        vm.showPage(Page.LIBRARY)
        delay(500)
        vm.focusSearch()
        delay(800)
        check(vm.isEditingText, "Focusing search marks text editing")
        text("vcn")
        check(state.page == Page.LIBRARY && state.libraryQuery == "vcn", "Shortcut keys typed in search reach the field (got “${state.libraryQuery}”)")
        key("KEYCODE_ESCAPE")
        check(!vm.isEditingText && state.libraryQuery == "vcn", "Esc leaves the search field and keeps the query")
        vm.setLibraryQuery("")
        delay(400)

        key("CTRL+KEYCODE_A")
        check(state.selection.size == state.libraryVisible.size && state.selecting, "Ctrl+A selects every shown video")
        key("KEYCODE_ESCAPE")
        check(state.selection.isEmpty() && !state.selecting, "Esc clears the selection")
        val index = state.combinationIndex
        key("KEYCODE_N")
        check(state.combinationIndex == index, "N does nothing outside the viewer")
    }

    private fun indexOf(predicate: (com.pvorg.android.domain.Combination) -> Boolean): Int? =
        state.combinations.indexOfFirst(predicate).takeIf { it >= 0 }

    private suspend fun shot(name: String, settle: Long = 1_000) {
        delay(settle)
        request("shot $name")
    }

    private suspend fun key(spec: String) {
        request("key $spec")
        delay(500)
    }

    private suspend fun text(value: String) {
        request("text $value")
        delay(600)
    }

    private fun check(condition: Boolean, description: String) = log("check ${if (condition) "PASS" else "FAIL"} $description")

    private suspend fun request(message: String) {
        ack = CompletableDeferred()
        log(message)
        withTimeoutOrNull(15_000) { ack.await() } ?: log("timeout waiting for host: $message")
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        while (!condition()) delay(200)
    }

    private fun log(message: String) = Log.i(TAG, message)

    companion object {
        const val TAG = "PVORG_TOUR"
        const val ACK_ACTION = "com.pvorg.android.QA_ACK"
    }
}
