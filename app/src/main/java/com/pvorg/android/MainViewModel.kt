package com.pvorg.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pvorg.android.data.PvorgRepository
import com.pvorg.android.model.AppConfigDto
import com.pvorg.android.model.AppTab
import com.pvorg.android.model.Combination
import com.pvorg.android.model.GalleryRecord
import com.pvorg.android.model.LoadedSession
import com.pvorg.android.model.MappingPageData
import com.pvorg.android.model.MappingVideoRow
import com.pvorg.android.model.SelectedRoot
import com.pvorg.android.model.TriState
import com.pvorg.android.model.VideoRecord
import com.pvorg.android.model.VideoRelationDto
import com.pvorg.android.util.CombinationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppPhase {
    REQUESTING_DIRECTORY,
    LOADING,
    READY,
    ERROR,
}

data class MainUiState(
    val phase: AppPhase = AppPhase.REQUESTING_DIRECTORY,
    val statusMessage: String = "Select your PVORG folder",
    val errorMessage: String? = null,
    val pickerRequestToken: Int = 1,
    val currentTab: AppTab = AppTab.HOME,
    val selectedRoot: SelectedRoot? = null,
    val config: AppConfigDto? = null,
    val videos: List<VideoRecord> = emptyList(),
    val galleries: List<GalleryRecord> = emptyList(),
    val combinations: List<Combination> = emptyList(),
    val combinationIndex: Int = 0,
    val showTgp: Boolean = true,
    val selectedGalleries: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
) {
    val currentCombination: Combination?
        get() = combinations.getOrNull(combinationIndex)

    val allGalleryNames: List<String>
        get() = galleries.map { it.name }.distinct().sorted()

    val allTags: List<String>
        get() = config?.tags?.distinct()?.sorted().orEmpty()
}

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = PvorgRepository(application)

    val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun requestDirectorySelection() {
        _uiState.update {
            it.copy(
                phase = AppPhase.REQUESTING_DIRECTORY,
                statusMessage = "Select your PVORG folder",
                errorMessage = null,
                pickerRequestToken = it.pickerRequestToken + 1,
            )
        }
    }

    fun onRootSelectionCancelled() {
        _uiState.update {
            it.copy(
                phase = AppPhase.ERROR,
                statusMessage = "",
                errorMessage = "Folder selection was canceled.",
            )
        }
    }

    fun onRootSelected(rootUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = AppPhase.LOADING,
                    statusMessage = "Loading files...",
                    errorMessage = null,
                )
            }

            runCatching { repository.loadSession(rootUri) }
                .onSuccess { session ->
                    applyLoadedSession(session)
                }
                .onFailure { error ->
                    player.pause()
                    player.clearMediaItems()
                    _uiState.update {
                        it.copy(
                            phase = AppPhase.ERROR,
                            statusMessage = "",
                            errorMessage = error.message ?: "Unable to load selected folder.",
                        )
                    }
                }
        }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun toggleGalleryFilter(name: String) {
        _uiState.update { state ->
            val updated = state.selectedGalleries.toMutableSet()
            if (!updated.add(name)) {
                updated.remove(name)
            }
            state.copy(selectedGalleries = updated)
        }
        rebuildCombinations()
    }

    fun toggleTagFilter(name: String) {
        _uiState.update { state ->
            val updated = state.selectedTags.toMutableSet()
            if (!updated.add(name)) {
                updated.remove(name)
            }
            state.copy(selectedTags = updated)
        }
        rebuildCombinations()
    }

    fun useAllGalleries() {
        _uiState.update { it.copy(selectedGalleries = emptySet()) }
        rebuildCombinations()
    }

    fun useAllTags() {
        _uiState.update { it.copy(selectedTags = emptySet()) }
        rebuildCombinations()
    }

    fun previousCombination() {
        if (_uiState.value.combinations.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                combinationIndex = (state.combinationIndex - 1).mod(state.combinations.size),
                showTgp = true,
            )
        }
        stopVideo()
    }

    fun nextCombination() {
        if (_uiState.value.combinations.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                combinationIndex = (state.combinationIndex + 1) % state.combinations.size,
                showTgp = true,
            )
        }
        stopVideo()
    }

    fun toggleViewerMode() {
        val state = _uiState.value
        val combination = state.currentCombination ?: return
        if (state.showTgp) {
            val mediaItem = MediaItem.fromUri(combination.video.videoUri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            _uiState.update { it.copy(showTgp = false) }
        } else {
            stopVideo()
            _uiState.update { it.copy(showTgp = true) }
        }
    }

    fun playPause() {
        if (_uiState.value.showTgp) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekRelative(deltaMs: Long) {
        if (_uiState.value.showTgp) return
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(target)
    }

    fun seekToPercent(percent: Double) {
        if (_uiState.value.showTgp) return
        val duration = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((duration * percent).toLong())
    }

    fun onShortcut(key: ShortcutKey): Boolean {
        when (key) {
            ShortcutKey.HOME -> setTab(AppTab.HOME)
            ShortcutKey.CONFIG -> setTab(AppTab.CONFIG)
            ShortcutKey.TOGGLE_VIEWER_MODE -> if (_uiState.value.currentTab == AppTab.HOME) toggleViewerMode() else return false
            ShortcutKey.PREVIOUS_COMBINATION -> if (_uiState.value.currentTab == AppTab.HOME) previousCombination() else return false
            ShortcutKey.NEXT_COMBINATION -> if (_uiState.value.currentTab == AppTab.HOME) nextCombination() else return false
            ShortcutKey.PLAY_PAUSE -> if (_uiState.value.currentTab == AppTab.HOME) playPause() else return false
            ShortcutKey.SEEK_BACK -> if (_uiState.value.currentTab == AppTab.HOME) seekRelative(-10_000L) else return false
            ShortcutKey.SEEK_FORWARD -> if (_uiState.value.currentTab == AppTab.HOME) seekRelative(10_000L) else return false
            is ShortcutKey.SEEK_PERCENT -> if (_uiState.value.currentTab == AppTab.HOME) seekToPercent(key.percent) else return false
        }
        return true
    }

    fun galleryMappingPage(page: Int, pageSize: Int, hideDecided: Boolean): MappingPageData {
        val state = _uiState.value
        val config = state.config ?: return MappingPageData.empty(pageSize)
        val columns = state.allGalleryNames
        if (columns.isEmpty()) return MappingPageData.empty(pageSize)

        val rows = state.videos.mapNotNull { video ->
            val relation = config.videoRelations[video.filename] ?: VideoRelationDto()
            val map = columns.associateWith { column ->
                when {
                    column in relation.yesGalleries -> TriState.YES
                    column in relation.noGalleries -> TriState.NO
                    else -> TriState.UNDECIDED
                }
            }
            if (hideDecided && map.values.all { it != TriState.UNDECIDED }) {
                null
            } else {
                MappingVideoRow(
                    filename = video.filename,
                    title = if (video.title.isBlank()) video.filename else video.title,
                    tgpUri = video.tgpUri,
                    map = map,
                )
            }
        }

        return paginate(rows, columns, page, pageSize)
    }

    fun tagMappingPage(page: Int, pageSize: Int, hideDecided: Boolean): MappingPageData {
        val state = _uiState.value
        val config = state.config ?: return MappingPageData.empty(pageSize)
        val columns = state.allTags
        if (columns.isEmpty()) return MappingPageData.empty(pageSize)

        val rows = state.videos.mapNotNull { video ->
            val relation = config.videoRelations[video.filename] ?: VideoRelationDto()
            val map = columns.associateWith { column ->
                when {
                    column in relation.yesTags -> TriState.YES
                    column in relation.noTags -> TriState.NO
                    else -> TriState.UNDECIDED
                }
            }
            if (hideDecided && map.values.all { it != TriState.UNDECIDED }) {
                null
            } else {
                MappingVideoRow(
                    filename = video.filename,
                    title = if (video.title.isBlank()) video.filename else video.title,
                    tgpUri = video.tgpUri,
                    map = map,
                )
            }
        }

        return paginate(rows, columns, page, pageSize)
    }

    suspend fun saveGalleryMappings(changes: Map<String, Map<String, TriState>>): Result<Unit> {
        val state = _uiState.value
        val root = state.selectedRoot ?: return Result.failure(IllegalStateException("No selected folder."))
        val config = state.config ?: return Result.failure(IllegalStateException("No loaded config."))

        return runCatching {
            val updatedConfig = repository.saveGalleryMappings(root, config, state.allGalleryNames, changes)
            _uiState.update { it.copy(config = updatedConfig) }
            rebuildCombinations()
        }
    }

    suspend fun saveTagMappings(changes: Map<String, Map<String, TriState>>): Result<Unit> {
        val state = _uiState.value
        val root = state.selectedRoot ?: return Result.failure(IllegalStateException("No selected folder."))
        val config = state.config ?: return Result.failure(IllegalStateException("No loaded config."))

        return runCatching {
            val updatedConfig = repository.saveTagMappings(root, config, state.allTags, changes)
            _uiState.update { it.copy(config = updatedConfig) }
            rebuildCombinations()
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    private fun applyLoadedSession(session: LoadedSession) {
        val allGalleryNames = session.galleries.map { it.name }.sorted().toSet()
        val combinations = CombinationEngine.generate(
            videos = session.videos,
            galleries = session.galleries,
            selectedGalleries = allGalleryNames,
            selectedTags = emptySet(),
        )

        stopVideo()
        _uiState.value = MainUiState(
            phase = AppPhase.READY,
            statusMessage = "",
            selectedRoot = session.selectedRoot,
            config = session.config,
            videos = session.videos,
            galleries = session.galleries,
            combinations = combinations,
            combinationIndex = 0,
            showTgp = true,
            selectedGalleries = allGalleryNames,
            selectedTags = emptySet(),
        )
    }

    private fun rebuildCombinations() {
        _uiState.update { state ->
            val combinations = CombinationEngine.generate(
                videos = state.videos,
                galleries = state.galleries,
                selectedGalleries = state.selectedGalleries,
                selectedTags = state.selectedTags,
            )
            state.copy(
                combinations = combinations,
                combinationIndex = 0,
                showTgp = true,
            )
        }
        stopVideo()
    }

    private fun stopVideo() {
        player.pause()
        player.clearMediaItems()
    }

    private fun paginate(
        rows: List<MappingVideoRow>,
        columns: List<String>,
        page: Int,
        pageSize: Int,
    ): MappingPageData {
        val total = rows.size
        val totalPages = maxOf(1, ((total + pageSize - 1) / pageSize))
        val clampedPage = page.coerceIn(1, totalPages)
        val start = (clampedPage - 1) * pageSize
        val end = minOf(start + pageSize, total)
        val pageRows = if (start < end) rows.subList(start, end) else emptyList()
        return MappingPageData(
            total = total,
            page = clampedPage,
            pageSize = pageSize,
            columns = columns,
            videos = pageRows,
        )
    }
}

sealed interface ShortcutKey {
    data object HOME : ShortcutKey
    data object CONFIG : ShortcutKey
    data object TOGGLE_VIEWER_MODE : ShortcutKey
    data object PREVIOUS_COMBINATION : ShortcutKey
    data object NEXT_COMBINATION : ShortcutKey
    data object PLAY_PAUSE : ShortcutKey
    data object SEEK_BACK : ShortcutKey
    data object SEEK_FORWARD : ShortcutKey
    data class SEEK_PERCENT(val percent: Double) : ShortcutKey
}
