package com.pvorg.android.state

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pvorg.android.domain.FilenameRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class Playback(
    val location: String? = null,
    val status: Status = Status.IDLE,
    val failure: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isScrubbing: Boolean = false,
) {
    enum class Status { IDLE, LOADING, READY, FAILED }

    val canControl: Boolean get() = status == Status.READY
}

/**
 * Wraps ExoPlayer for the viewer's video mode. Position is published on its
 * own flow so only the transport bar recomposes while a video plays.
 */
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }

    private val _state = MutableStateFlow(Playback())
    val state: StateFlow<Playback> = _state.asStateFlow()

    private var filename: String? = null
    private var ticker: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val frameDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var retriever: MediaMetadataRetriever? = null
    private var retrieverLocation: String? = null
    private val frames = LruCache<Long, Bitmap>(120)

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startTicker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> _state.update {
                        it.copy(status = Playback.Status.READY, durationMs = player.duration.coerceAtLeast(0))
                    }
                    Player.STATE_ENDED -> _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                player.pause()
                _state.update { it.copy(status = Playback.Status.FAILED, isPlaying = false, failure = failureMessage(error)) }
            }
        })
    }

    /** Loads and plays; returning to the already-loaded video resumes where it paused. */
    fun play(location: String, filename: String) {
        if (_state.value.location != location || _state.value.status == Playback.Status.IDLE) {
            stop()
            this.filename = filename
            _state.value = Playback(location = location, status = Playback.Status.LOADING)
            player.setMediaItem(MediaItem.fromUri(location))
            player.prepare()
        }
        if (_state.value.status == Playback.Status.FAILED) return
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    fun pause() = player.pause()

    fun togglePlayPause() {
        if (!_state.value.canControl) return
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun seekTo(ms: Long) {
        val duration = _state.value.durationMs
        if (duration <= 0) return
        val clamped = ms.coerceIn(0, duration)
        player.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun seekToFraction(fraction: Float) = seekTo((_state.value.durationMs * fraction).toLong())

    /** Live scrubbing: the bar owns the position while dragging; the release seeks exactly. */
    fun scrub(ms: Long, finished: Boolean) {
        val duration = _state.value.durationMs
        if (duration <= 0) return
        val clamped = ms.coerceIn(0, duration)
        _state.update { it.copy(positionMs = clamped, isScrubbing = !finished) }
        player.seekTo(clamped)
    }

    fun stop() {
        ticker?.cancel()
        player.stop()
        player.clearMediaItems()
        filename = null
        frames.evictAll()
        _state.value = Playback()
    }

    /**
     * The frame at the start of [atMs]'s second, decoded off the main thread and
     * cached per second. OPTION_CLOSEST decodes the exact frame; CLOSEST_SYNC
     * would show the nearest keyframe, which can be seconds away.
     */
    suspend fun frame(atMs: Long): Bitmap? {
        val location = _state.value.location ?: return null
        val second = (atMs / 1000).coerceAtLeast(0)
        frames.get(second)?.let { return it }
        return withContext(frameDispatcher) {
            runCatching {
                val retriever = retrieverFor(location)
                retriever.getScaledFrameAtTime(second * 1_000_000, MediaMetadataRetriever.OPTION_CLOSEST, 448, 252)
            }.getOrNull()?.also { if (_state.value.location == location) frames.put(second, it) }
        }
    }

    private fun retrieverFor(location: String): MediaMetadataRetriever {
        if (retrieverLocation != location) {
            retriever?.release()
            retriever = MediaMetadataRetriever().apply { setDataSource(context, Uri.parse(location)) }
            retrieverLocation = location
        }
        return retriever!!
    }

    fun release() {
        ticker?.cancel()
        player.release()
        retriever?.release()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && player.isPlaying) {
                if (!_state.value.isScrubbing) _state.update { it.copy(positionMs = player.currentPosition) }
                delay(100)
            }
            if (!_state.value.isScrubbing) _state.update { it.copy(positionMs = player.currentPosition) }
        }
    }

    private fun failureMessage(error: PlaybackException): String {
        val ext = filename?.let(FilenameRules::extension).orEmpty()
        val unsupported = error.errorCode in setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        )
        return if (unsupported && ext.isNotEmpty()) {
            "Android can’t play this .$ext file. Open it in an app that supports this format, or re-encode it to MP4."
        } else {
            "The video couldn’t be opened. The file may be damaged or use an unsupported codec."
        }
    }
}
