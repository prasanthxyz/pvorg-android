package com.pvorg.android.state

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pvorg.android.data.LibraryRepository
import com.pvorg.android.data.LibraryStorage
import com.pvorg.android.data.LoadProgress
import com.pvorg.android.data.SafLibraryStorage
import com.pvorg.android.domain.CombinationEngine
import com.pvorg.android.domain.DisplayName
import com.pvorg.android.domain.Filters
import com.pvorg.android.domain.Format
import com.pvorg.android.domain.Library
import com.pvorg.android.domain.LibraryRules
import com.pvorg.android.domain.MappingKind
import com.pvorg.android.domain.MappingRow
import com.pvorg.android.domain.PairingExclusion
import com.pvorg.android.domain.Search
import com.pvorg.android.domain.TriState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Preferences(application)
    val playerController = PlayerController(application, viewModelScope)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _appearance = MutableStateFlow(prefs.appearance)
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _navigationExpanded = MutableStateFlow(prefs.navigationExpanded)
    val navigationExpanded: StateFlow<Boolean> = _navigationExpanded.asStateFlow()

    private val _navigationHidden = MutableStateFlow(prefs.navigationHidden)
    val navigationHidden: StateFlow<Boolean> = _navigationHidden.asStateFlow()

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val toasts: SharedFlow<Toast> = _toasts.asSharedFlow()

    /** Set by search fields; while true, single-key shortcuts stand down (spec §7.1). */
    @Volatile var isEditingText: Boolean = false

    private var repository: LibraryRepository? = null
    private var loadJob: Job? = null
    private val writes = Mutex()
    private val undoStack = ArrayDeque<PairingExclusion>()
    private val redoStack = ArrayDeque<PairingExclusion>()
    private var started = false

    // region Library lifecycle

    /** Opens the debug QA library, the remembered folder, or asks for one. */
    fun start(override: LibraryStorage?) {
        if (started) return
        started = true
        if (override != null) return open(override)
        val saved = prefs.libraryUri?.let(Uri::parse)
        val resolver = getApplication<Application>().contentResolver
        val granted = saved != null && resolver.persistedUriPermissions.any { it.uri == saved && it.isReadPermission && it.isWritePermission }
        if (granted) open(SafLibraryStorage(resolver, saved!!)) else _state.update { it.copy(phase = Phase.NeedsFolder) }
    }

    fun onFolderPicked(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        prefs.libraryUri?.let(Uri::parse)?.takeIf { it != uri }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
        prefs.libraryUri = uri.toString()
        open(SafLibraryStorage(resolver, uri))
    }

    private fun open(storage: LibraryStorage) {
        repository = LibraryRepository(storage)
        _state.update { it.copy(locationDescription = storage.locationDescription) }
        reload()
    }

    /** Spec §3.2: filters reset; unsaved mapping changes survive except for videos that no longer exist. */
    fun reload() {
        val repository = repository ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            playerController.stop()
            _state.update { it.copy(phase = Phase.Loading(LoadProgress("Loading Configuration"))) }
            try {
                val library = withContext(Dispatchers.IO) {
                    repository.load { progress -> _state.update { it.copy(phase = Phase.Loading(progress)) } }
                }
                undoStack.clear()
                redoStack.clear()
                _state.update { state ->
                    applying(state, library, resetFilters = true).copy(
                        phase = Phase.Ready,
                        viewerMode = ViewerMode.CONTACT_SHEET,
                        selection = emptySet(),
                        selecting = false,
                        canUndo = false,
                        canRedo = false,
                    )
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { it.copy(phase = Phase.Failed(error.message ?: "Unknown error", repository.displayName)) }
            }
        }
    }

    /** Recomputes everything derived from the library. Combinations regenerate (and reshuffle). */
    private fun applying(state: UiState, library: Library, resetFilters: Boolean): UiState {
        val filters = if (resetFilters) Filters.defaults(library) else state.filters.reconciled(library)
        return derived(
            state.copy(
                library = library,
                filters = filters,
                combinations = CombinationEngine.generate(library, filters),
                combinationIndex = 0,
                sessions = state.sessions.mapValues { (_, session) -> session.pruned(library) },
            ),
        )
    }

    /** Mapping rows, counts and the visible library: recomputed only when their inputs change. */
    private fun derived(state: UiState): UiState {
        val library = state.library ?: return state
        val rows = MappingKind.entries.associateWith { kind ->
            val session = state.session(kind)
            LibraryRules.mappingRows(library, kind, hideDecided = !session.showsDecided, query = session.query)
        }
        val undecided = MappingKind.entries.associateWith { kind ->
            val session = state.session(kind)
            if (!session.showsDecided && session.query.isBlank()) rows.getValue(kind).size
            else LibraryRules.mappingRows(library, kind, hideDecided = true, query = "").size
        }
        val query = state.libraryQuery.trim()
        val visible = if (query.isEmpty()) library.videos else library.videos.filter { Search.matches(it, query) }
        val selection = LibrarySelection.pruned(state.selection, visible.map { it.filename })
        return state.copy(
            mappingRows = rows,
            undecidedCounts = undecided,
            libraryVisible = visible,
            selection = selection,
            selecting = state.selecting && selection.isNotEmpty(),
        )
    }

    // endregion

    // region Navigation and appearance

    fun showPage(page: Page) {
        // A search field removed while focused may never report losing focus.
        isEditingText = false
        if (page != Page.VIEWER) leaveVideo()
        _state.update { it.copy(page = page, selecting = if (page == Page.LIBRARY) it.selecting else false) }
    }

    fun setAppearance(appearance: Appearance) {
        prefs.appearance = appearance
        _appearance.value = appearance
    }

    fun setNavigationExpanded(expanded: Boolean) {
        prefs.navigationExpanded = expanded
        _navigationExpanded.value = expanded
    }

    /** Hides or shows the navigation; showing restores the drawer or rail last used (spec §4.1). */
    fun setNavigationHidden(hidden: Boolean) {
        prefs.navigationHidden = hidden
        _navigationHidden.value = hidden
    }

    fun toggleNavigationHidden() = setNavigationHidden(!_navigationHidden.value)

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // endregion

    // region Filters

    fun setFilterQuery(query: String) = _state.update { it.copy(filterQuery = query) }

    fun togglePreviewPanel() = _state.update { it.copy(showsPreviewPanel = !it.showsPreviewPanel) }

    fun toggleFilter(section: FilterSection, value: String) = updateFilter(section) { if (value in it) it - value else it + value }

    fun includeAll(section: FilterSection) = updateFilter(section) { allValues(section) }

    fun includeNone(section: FilterSection) = updateFilter(section) { emptySet() }

    fun only(section: FilterSection, value: String) = updateFilter(section) { setOf(value) }

    fun allExcept(section: FilterSection, value: String) = updateFilter(section) { allValues(section) - value }

    fun resetFilters() {
        val library = _state.value.library ?: return
        regenerate(Filters.defaults(library))
    }

    fun allValues(section: FilterSection): Set<String> {
        val library = _state.value.library ?: return emptySet()
        return when (section) {
            FilterSection.GALLERIES -> library.galleryNames
            FilterSection.STUDIOS -> library.studios
            FilterSection.TAGS -> library.tags
            FilterSection.CAST -> library.cast
        }.toSet()
    }

    private fun updateFilter(section: FilterSection, transform: (Set<String>) -> Set<String>) {
        val filters = _state.value.filters
        regenerate(
            when (section) {
                FilterSection.GALLERIES -> filters.copy(galleries = transform(filters.galleries))
                FilterSection.STUDIOS -> filters.copy(studios = transform(filters.studios))
                FilterSection.TAGS -> filters.copy(tags = transform(filters.tags))
                FilterSection.CAST -> filters.copy(cast = transform(filters.cast))
            },
        )
    }

    private fun regenerate(filters: Filters) {
        val library = _state.value.library ?: return
        leaveVideo()
        _state.update { it.copy(filters = filters, combinations = CombinationEngine.generate(library, filters), combinationIndex = 0) }
    }

    // endregion

    // region Viewer

    fun nextCombination() = moveBy(1)

    fun previousCombination() = moveBy(-1)

    private fun moveBy(delta: Int) {
        val count = _state.value.combinations.size
        if (count == 0) return
        leaveVideo()
        _state.update { it.copy(combinationIndex = (it.combinationIndex + delta).mod(count)) }
    }

    /** Opens a combination from Up Next. */
    fun showCombination(index: Int) {
        if (index !in _state.value.combinations.indices) return
        leaveVideo()
        _state.update { it.copy(combinationIndex = index, page = Page.VIEWER) }
    }

    fun setViewerMode(mode: ViewerMode) {
        val combination = _state.value.currentCombination ?: return
        when (mode) {
            ViewerMode.VIDEO -> playerController.play(combination.video.location, combination.video.filename)
            ViewerMode.CONTACT_SHEET -> playerController.pause()
        }
        _state.update { it.copy(viewerMode = mode) }
    }

    fun toggleViewerMode() = setViewerMode(
        if (_state.value.viewerMode == ViewerMode.VIDEO) ViewerMode.CONTACT_SHEET else ViewerMode.VIDEO,
    )

    /** Moving to another combination stops playback and returns to the contact sheet. */
    private fun leaveVideo() {
        playerController.stop()
        _state.update { it.copy(viewerMode = ViewerMode.CONTACT_SHEET) }
    }

    /** Spec §3.6: no confirmation, removed in place, undoable. */
    fun excludeCurrentPairing() {
        val combination = _state.value.currentCombination ?: return
        viewModelScope.launch {
            val exclusion = exclude(combination.video.filename, combination.gallery.name) ?: return@launch
            redoStack.clear()
            publishUndo()
            _toasts.emit(excludedToast(exclusion))
        }
    }

    private fun videoTitle(filename: String) = _state.value.library?.video(filename)?.let(DisplayName::title) ?: filename

    private fun excludedToast(exclusion: PairingExclusion) = Toast(
        message = "Excluded “${exclusion.galleryName}” from “${videoTitle(exclusion.filename)}”",
        actionLabel = "Undo",
        action = { if (undoStack.lastOrNull() == exclusion) undo() },
    )

    fun undo() {
        val exclusion = undoStack.removeLastOrNull() ?: return
        viewModelScope.launch {
            if (restore(exclusion)) {
                redoStack.addLast(exclusion)
                // Also replaces the "Excluded …" snackbar, whose Undo no longer applies.
                _toasts.emit(Toast("Restored “${exclusion.galleryName}” for “${videoTitle(exclusion.filename)}”"))
            } else {
                undoStack.addLast(exclusion)
            }
            publishUndo()
        }
    }

    fun redo() {
        val exclusion = redoStack.removeLastOrNull() ?: return
        viewModelScope.launch {
            val again = exclude(exclusion.filename, exclusion.galleryName)
            if (again == null) redoStack.addLast(exclusion) else _toasts.emit(excludedToast(again))
            publishUndo()
        }
    }

    private fun publishUndo() = _state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }

    private suspend fun exclude(filename: String, gallery: String): PairingExclusion? = writes.withLock {
        val repository = repository ?: return null
        val library = _state.value.library ?: return null
        try {
            val (updated, exclusion) = withContext(Dispatchers.IO) { repository.excludePairing(library, filename, gallery) }
            val state = _state.value
            val index = state.combinations.indexOfFirst { it.video.filename == filename && it.gallery.name == gallery }
            val combinations = if (index >= 0) state.combinations.filterIndexed { i, _ -> i != index } else state.combinations
            var newIndex = state.combinationIndex
            if (index in 0 until newIndex) newIndex -= 1
            if (newIndex >= combinations.size) newIndex = 0
            if (index == state.combinationIndex) playerController.stop()
            _state.update {
                derived(
                    it.copy(
                        library = updated,
                        combinations = combinations,
                        combinationIndex = newIndex,
                        viewerMode = if (index == it.combinationIndex) ViewerMode.CONTACT_SHEET else it.viewerMode,
                        sessions = it.sessions + (MappingKind.GALLERIES to it.session(MappingKind.GALLERIES).discarding(filename, gallery)),
                    ),
                )
            }
            exclusion.copy(index = index.takeIf { it >= 0 }).also { undoStack.addLast(it) }
        } catch (error: Exception) {
            _state.update { it.copy(notice = Notice("Couldn’t Exclude Pairing", error.message.orEmpty())) }
            null
        }
    }

    private suspend fun restore(exclusion: PairingExclusion): Boolean = writes.withLock {
        val repository = repository ?: return false
        val library = _state.value.library ?: return false
        try {
            val updated = withContext(Dispatchers.IO) { repository.restorePairing(library, exclusion) }
            _state.update { state ->
                var next = state.copy(library = updated)
                val video = updated.video(exclusion.filename)
                val gallery = updated.galleries.firstOrNull { it.name == exclusion.galleryName }
                val present = state.combinations.any { it.video.filename == exclusion.filename && it.gallery.name == exclusion.galleryName }
                if (video != null && gallery != null && !present &&
                    CombinationEngine.includes(video, state.filters) && CombinationEngine.includes(gallery, video, state.filters)
                ) {
                    val index = (exclusion.index ?: state.combinationIndex).coerceIn(0, state.combinations.size)
                    val combinations = state.combinations.toMutableList().apply { add(index, com.pvorg.android.domain.Combination(video, gallery)) }
                    playerController.stop()
                    next = next.copy(combinations = combinations, combinationIndex = index, viewerMode = ViewerMode.CONTACT_SHEET)
                }
                derived(next)
            }
            true
        } catch (error: Exception) {
            _state.update { it.copy(notice = Notice("Couldn’t Undo", error.message.orEmpty())) }
            false
        }
    }

    // endregion

    // region Library page

    fun setLibraryQuery(query: String) = _state.update { derived(it.copy(libraryQuery = query)) }

    fun startSelection(filename: String) = _state.update { it.copy(selecting = true, selection = it.selection + filename) }

    fun toggleSelection(filename: String) = _state.update {
        val selection = if (filename in it.selection) it.selection - filename else it.selection + filename
        it.copy(selection = selection, selecting = selection.isNotEmpty())
    }

    fun selectAllVisible() = _state.update {
        it.copy(selection = it.libraryVisible.map { video -> video.filename }.toSet(), selecting = it.libraryVisible.isNotEmpty())
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet(), selecting = false) }

    fun requestDelete(filenames: Collection<String>) = _state.update { it.copy(pendingDeletion = filenames.sorted(), libraryPreview = null) }

    fun showPreview(filename: String?) = _state.update { it.copy(libraryPreview = filename) }

    fun showSkippedFiles(shows: Boolean) = _state.update { it.copy(showsSkippedFiles = shows) }

    fun focusSearch() = _state.update { it.copy(searchFocusRequest = it.searchFocusRequest + 1) }

    fun cancelDelete() = _state.update { it.copy(pendingDeletion = emptyList()) }

    /** Spec §3.7. Afterwards filters reset, like a rescan. */
    fun confirmDelete() {
        val filenames = _state.value.pendingDeletion
        _state.update { it.copy(pendingDeletion = emptyList()) }
        if (filenames.isEmpty()) return
        viewModelScope.launch {
            writes.withLock {
                val repository = repository ?: return@launch
                val library = _state.value.library ?: return@launch
                try {
                    val (updated, result) = withContext(Dispatchers.IO) { repository.deleteVideos(library, filenames) }
                    playerController.stop()
                    _state.update { state ->
                        applying(state.copy(selection = state.selection - filenames.toSet()), updated, resetFilters = true)
                            .copy(viewerMode = ViewerMode.CONTACT_SHEET)
                    }
                    if (result.errors.isEmpty()) {
                        _toasts.emit(Toast("Deleted ${Format.counted(result.deletedCount, "video")}"))
                    } else {
                        _state.update {
                            it.copy(
                                notice = Notice(
                                    "Some Videos Couldn’t Be Deleted",
                                    "Deleted ${Format.counted(result.deletedCount, "video")}.\n\n" + result.errors.joinToString("\n"),
                                ),
                            )
                        }
                    }
                } catch (error: Exception) {
                    _state.update { it.copy(notice = Notice("Delete Failed", error.message.orEmpty())) }
                }
            }
        }
    }

    // endregion

    // region Mapping

    fun cycleCell(kind: MappingKind, row: MappingRow, column: String) = updateSession(kind) { it.cycling(row, column) }

    fun setColumn(kind: MappingKind, column: String, value: TriState) =
        updateSession(kind) { it.settingColumn(value, column, _state.value.mappingRows[kind].orEmpty()) }

    fun setRow(kind: MappingKind, row: MappingRow, value: TriState) =
        updateSession(kind) { it.settingRow(value, row, _state.value.library?.columns(kind).orEmpty()) }

    fun setShowsDecided(kind: MappingKind, showsDecided: Boolean) =
        _state.update { derived(it.copy(sessions = it.sessions + (kind to it.session(kind).copy(showsDecided = showsDecided)))) }

    fun setMappingQuery(kind: MappingKind, query: String) =
        _state.update { derived(it.copy(sessions = it.sessions + (kind to it.session(kind).copy(query = query)))) }

    fun requestRevert(kind: MappingKind) = _state.update { it.copy(confirmsRevert = kind) }

    fun dismissRevert() = _state.update { it.copy(confirmsRevert = null) }

    fun confirmRevert() = _state.update { state ->
        val kind = state.confirmsRevert ?: return@update state
        state.copy(confirmsRevert = null, sessions = state.sessions + (kind to state.session(kind).reverted()))
    }

    /** Spec §3.3: one atomic write, then the combination list regenerates. */
    fun saveMappings(kind: MappingKind) {
        val session = _state.value.session(kind)
        if (!session.hasChanges) return
        viewModelScope.launch {
            writes.withLock {
                val repository = repository ?: return@launch
                val library = _state.value.library ?: return@launch
                try {
                    val updated = withContext(Dispatchers.IO) { repository.saveMappings(library, kind, session.pending) }
                    leaveVideo()
                    _state.update { state ->
                        applying(state.copy(sessions = state.sessions + (kind to state.session(kind).reverted())), updated, resetFilters = false)
                    }
                    _toasts.emit(Toast("Saved ${Format.counted(session.changeCount, "change")}"))
                } catch (error: Exception) {
                    _state.update { it.copy(notice = Notice("Couldn’t Save Changes", error.message.orEmpty())) }
                }
            }
        }
    }

    private fun updateSession(kind: MappingKind, transform: (MappingSession) -> MappingSession) =
        _state.update { it.copy(sessions = it.sessions + (kind to transform(it.session(kind)))) }

    // endregion

    // region Stale entries

    fun requestStaleCleanup() {
        viewModelScope.launch {
            val repository = repository ?: return@launch
            val library = _state.value.library ?: return@launch
            try {
                val preview = withContext(Dispatchers.IO) { repository.stalePreview(library) }
                _state.update { it.copy(stalePreview = preview) }
            } catch (error: Exception) {
                _state.update { it.copy(notice = Notice("Cleanup Unavailable", error.message.orEmpty())) }
            }
        }
    }

    fun dismissStaleCleanup() = _state.update { it.copy(stalePreview = null) }

    fun confirmStaleCleanup() {
        val preview = _state.value.stalePreview ?: return
        _state.update { it.copy(stalePreview = null) }
        viewModelScope.launch {
            writes.withLock {
                val repository = repository ?: return@launch
                val library = _state.value.library ?: return@launch
                try {
                    val (updated, removed) = withContext(Dispatchers.IO) { repository.removeStaleEntries(library, preview) }
                    _state.update { applying(it, updated, resetFilters = false) }
                    _toasts.emit(Toast("Removed ${Format.counted(removed, "stale entry", "stale entries")}"))
                } catch (error: Exception) {
                    _state.update { it.copy(notice = Notice("Cleanup Failed", error.message.orEmpty())) }
                }
            }
        }
    }

    // endregion

    // region Keyboard

    /**
     * Hardware-keyboard shortcuts (spec §7.1). Returns whether the key was
     * handled. Callers skip this while text is being edited.
     */
    fun onKey(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): Boolean {
        val state = _state.value
        if (state.phase != Phase.Ready || alt) return false
        if (ctrl) {
            return when (keyCode) {
                KeyEvent.KEYCODE_Z -> if (shift) redo() else undo()
                KeyEvent.KEYCODE_A -> if (state.page == Page.LIBRARY) selectAllVisible() else return false
                KeyEvent.KEYCODE_S -> state.page.mappingKind?.let(::saveMappings) ?: return false
                KeyEvent.KEYCODE_R -> reload()
                KeyEvent.KEYCODE_BACKSLASH -> toggleNavigationHidden()
                KeyEvent.KEYCODE_F -> if (state.page in setOf(Page.FILTERS, Page.LIBRARY, Page.GALLERY_MAPPING, Page.TAG_MAPPING)) focusSearch() else return false
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL ->
                    if (state.page == Page.LIBRARY && state.selection.isNotEmpty()) requestDelete(state.selection) else return false
                else -> return false
            }.let { true }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_V -> return showPage(Page.VIEWER).let { true }
            KeyEvent.KEYCODE_C -> return showPage(Page.FILTERS).let { true }
            KeyEvent.KEYCODE_ESCAPE -> if (state.page == Page.LIBRARY && state.selecting) return clearSelection().let { true }
        }
        if (state.page != Page.VIEWER || state.currentCombination == null) return false
        val playback = playerController.state.value
        val playing = state.viewerMode == ViewerMode.VIDEO && playback.canControl
        when (keyCode) {
            KeyEvent.KEYCODE_P -> toggleViewerMode()
            KeyEvent.KEYCODE_B -> previousCombination()
            KeyEvent.KEYCODE_N -> nextCombination()
            KeyEvent.KEYCODE_DEL -> excludeCurrentPairing()
            KeyEvent.KEYCODE_SPACE -> if (playing) playerController.togglePlayPause() else return false
            KeyEvent.KEYCODE_DPAD_LEFT -> if (playing) playerController.seekBy(-10_000) else return false
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (playing) playerController.seekBy(10_000) else return false
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                if (playing) playerController.seekToFraction((keyCode - KeyEvent.KEYCODE_0) / 10f) else return false
            else -> return false
        }
        return true
    }

    // endregion

    override fun onCleared() {
        playerController.release()
        super.onCleared()
    }
}
