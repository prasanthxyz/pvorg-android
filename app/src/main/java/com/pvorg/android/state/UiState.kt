package com.pvorg.android.state

import androidx.compose.runtime.Immutable
import com.pvorg.android.data.LoadProgress
import com.pvorg.android.domain.Combination
import com.pvorg.android.domain.Filters
import com.pvorg.android.domain.Library
import com.pvorg.android.domain.MappingKind
import com.pvorg.android.domain.MappingRow
import com.pvorg.android.domain.StaleCleanupPreview
import com.pvorg.android.domain.Video

sealed interface Phase {
    /** First run: no library folder chosen yet. */
    data object NeedsFolder : Phase
    data class Loading(val progress: LoadProgress) : Phase
    data object Ready : Phase
    data class Failed(val message: String, val folderName: String?) : Phase
}

/** Pages and groups (spec §4). */
enum class Page(val title: String, val group: Group) {
    VIEWER("Viewer", Group.BROWSE),
    FILTERS("Filters", Group.BROWSE),
    LIBRARY("Library", Group.ORGANIZE),
    GALLERY_MAPPING("Gallery Mapping", Group.ORGANIZE),
    TAG_MAPPING("Tag Mapping", Group.ORGANIZE),
    SETTINGS("Settings", Group.NONE),
    ;

    enum class Group(val title: String) { BROWSE("Browse"), ORGANIZE("Organize"), NONE("") }

    val mappingKind: MappingKind?
        get() = when (this) {
            GALLERY_MAPPING -> MappingKind.GALLERIES
            TAG_MAPPING -> MappingKind.TAGS
            else -> null
        }

    /** Single-key navigation carried over from the original apps (spec §7.1). */
    val shortcut: String?
        get() = when (this) {
            VIEWER -> "V"
            FILTERS -> "C"
            else -> null
        }
}

enum class ViewerMode { CONTACT_SHEET, VIDEO }

enum class FilterSection(val title: String, val requires: Boolean) {
    GALLERIES("Galleries", requires = false),
    STUDIOS("Studios", requires = false),
    TAGS("Tags", requires = true),
    CAST("Cast", requires = true),
}

/** An error that needs acknowledgement (spec §7.2). */
data class Notice(val title: String, val message: String)

/** A brief confirmation, optionally with an action such as Undo. */
data class Toast(
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

@Immutable
data class UiState(
    val phase: Phase = Phase.NeedsFolder,
    val library: Library? = null,
    val locationDescription: String? = null,
    val filters: Filters = Filters(),
    val combinations: List<Combination> = emptyList(),
    val combinationIndex: Int = 0,
    val viewerMode: ViewerMode = ViewerMode.CONTACT_SHEET,
    val page: Page = Page.VIEWER,
    val filterQuery: String = "",
    val showsPreviewPanel: Boolean = true,
    val sessions: Map<MappingKind, MappingSession> = MappingKind.entries.associateWith { MappingSession(it) },
    val mappingRows: Map<MappingKind, List<MappingRow>> = emptyMap(),
    val undecidedCounts: Map<MappingKind, Int> = emptyMap(),
    val confirmsRevert: MappingKind? = null,
    val libraryQuery: String = "",
    val libraryVisible: List<Video> = emptyList(),
    val selection: Set<String> = emptySet(),
    val selecting: Boolean = false,
    val pendingDeletion: List<String> = emptyList(),
    /** Filename shown in the Library's large preview (the touch replacement for hover). */
    val libraryPreview: String? = null,
    val showsSkippedFiles: Boolean = false,
    /** Bumped to move focus to the current page's search field (Ctrl+F, and the QA tour). */
    val searchFocusRequest: Int = 0,
    val stalePreview: StaleCleanupPreview? = null,
    val notice: Notice? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val currentCombination: Combination? get() = combinations.getOrNull(combinationIndex)

    fun session(kind: MappingKind): MappingSession = sessions.getValue(kind)
}
