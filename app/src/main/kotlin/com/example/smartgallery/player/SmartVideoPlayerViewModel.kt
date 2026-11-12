package com.example.smartgallery.player

import android.content.Context
import android.graphics.Bitmap
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.smartgallery.data.db.Bookmark
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

private const val TAG = "SmartVideoPlayerVM"

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Supporting data types
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/** Represents a named chapter within a media item. */
data class Chapter(
    val titleMs: Long,
    val label: String,
)

/** State machine for the GIF recorder. */
sealed class GifMakerState {
    object Idle : GifMakerState()
    data class Recording(val progressMs: Long) : GifMakerState()
    data class Done(val path: String) : GifMakerState()
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// ViewModel
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@HiltViewModel
class SmartVideoPlayerViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val db: GalleryDatabase,
    private val id3Engine: LyricsAndId3Engine,
    private val vaultManager: com.example.smartgallery.vault.VaultAndFileManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    suspend fun readAudioId3(path: String) = id3Engine.readId3Metadata(path)

    suspend fun updateAudioId3(
        path: String,
        title: String,
        artist: String,
        album: String,
        year: String,
        genre: String = "",
        artworkFile: File? = null
    ): File {
        val file = id3Engine.updateId3Metadata(path, title, artist, album, year, genre, artworkFile)
        withContext(Dispatchers.IO) {
            val item = db.mediaItemDao().getByPath(path)
            item?.let {
                db.mediaItemDao().update(it.copy(displayName = "$title.mp3"))
            }
        }
        return file
    }

    // â”€â”€ Playback state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _isPlaying         = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> get() = _isPlaying.asStateFlow()

    private val _currentPosition   = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> get() = _currentPosition.asStateFlow()

    private val _duration          = MutableStateFlow(0L)
    val duration: StateFlow<Long> get() = _duration.asStateFlow()

    private val _bufferedPosition  = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> get() = _bufferedPosition.asStateFlow()

    // â”€â”€ Playback parameters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _playbackSpeed     = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> get() = _playbackSpeed.asStateFlow()

    private val _volume            = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> get() = _volume.asStateFlow()

    private val _isMuted           = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> get() = _isMuted.asStateFlow()

    // â”€â”€ Video transform â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _aspectRatioMode   = MutableStateFlow("FIT")
    val aspectRatioMode: StateFlow<String> get() = _aspectRatioMode.asStateFlow()

    private val _videoRotationDegrees = MutableStateFlow(0)
    val videoRotationDegrees: StateFlow<Int> get() = _videoRotationDegrees.asStateFlow()

    private val _cropMode          = MutableStateFlow("NONE")
    val cropMode: StateFlow<String> get() = _cropMode.asStateFlow()

    // â”€â”€ UI state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _screenLocked      = MutableStateFlow(false)
    val screenLocked: StateFlow<Boolean> get() = _screenLocked.asStateFlow()

    private val _showCodecHud      = MutableStateFlow(false)
    val showCodecHud: StateFlow<Boolean> get() = _showCodecHud.asStateFlow()

    private val _showControls      = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> get() = _showControls.asStateFlow()
    private var controlsHideJob: Job? = null

    // â”€â”€ A-B repeat â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _abRepeatAMs       = MutableStateFlow(-1L)
    val abRepeatAMs: StateFlow<Long> get() = _abRepeatAMs.asStateFlow()

    private val _abRepeatBMs       = MutableStateFlow(-1L)
    val abRepeatBMs: StateFlow<Long> get() = _abRepeatBMs.asStateFlow()

    // â”€â”€ Bookmarks & chapters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _currentBookmarks  = MutableStateFlow<List<Bookmark>>(emptyList())
    val currentBookmarks: StateFlow<List<Bookmark>> get() = _currentBookmarks.asStateFlow()

    private val _currentChapters   = MutableStateFlow<List<Chapter>>(emptyList())
    val currentChapters: StateFlow<List<Chapter>> get() = _currentChapters.asStateFlow()

    // â”€â”€ Playlist â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _playlistItems     = MutableStateFlow<List<MediaItem>>(emptyList())
    val playlistItems: StateFlow<List<MediaItem>> get() = _playlistItems.asStateFlow()

    private val _currentPlaylistIndex = MutableStateFlow(0)
    val currentPlaylistIndex: StateFlow<Int> get() = _currentPlaylistIndex.asStateFlow()

    // â”€â”€ Codec info â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _codecInfo         = MutableStateFlow("")
    val codecInfo: StateFlow<String> get() = _codecInfo.asStateFlow()

    // â”€â”€ Brightness â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _brightnessValue   = MutableStateFlow(-1f)
    val brightnessValue: StateFlow<Float> get() = _brightnessValue.asStateFlow()

    // â”€â”€ Volume boost â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _volumeBoostPercent = MutableStateFlow(100f)
    val volumeBoostPercent: StateFlow<Float> get() = _volumeBoostPercent.asStateFlow()

    // â”€â”€ Picture-in-picture â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _isInPip           = MutableStateFlow(false)
    val isInPip: StateFlow<Boolean> get() = _isInPip.asStateFlow()

    // â”€â”€ Background audio â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _isBackgroundAudio = MutableStateFlow(false)
    val isBackgroundAudio: StateFlow<Boolean> get() = _isBackgroundAudio.asStateFlow()

    // â”€â”€ GIF maker â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _gifMakerState     = MutableStateFlow<GifMakerState>(GifMakerState.Idle)
    val gifMakerState: StateFlow<GifMakerState> get() = _gifMakerState.asStateFlow()

    // â”€â”€ Event flows â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _pipEvent          = MutableSharedFlow<Boolean>()
    val pipEvent: SharedFlow<Boolean> get() = _pipEvent.asSharedFlow()

    private val _backEvent         = MutableSharedFlow<Unit>()
    val backEvent: SharedFlow<Unit> get() = _backEvent.asSharedFlow()

    private val _orientationToggleEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val orientationToggleEvent: SharedFlow<Unit> get() = _orientationToggleEvent.asSharedFlow()

    fun toggleScreenOrientation() {
        _orientationToggleEvent.tryEmit(Unit)
    }

    // â”€â”€ Player reference â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private var player: Player? = null
    val currentPlayer: Player? get() = player
    private var playerListener: Player.Listener? = null
    private var currentMediaPath: String? = null

    // Hardware Audio Effects
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // LibVLC fallback flag
    private val _useVlcFallback = MutableStateFlow(false)
    val useVlcFallback: StateFlow<Boolean> = _useVlcFallback.asStateFlow()
    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    // â”€â”€ A-B repeat polling â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private var abRepeatJob: Job? = null

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Player attachment
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun initPlayer(player: Player) {
        this.player = player

        (player as? androidx.media3.exoplayer.ExoPlayer)?.let { exo ->
            bindAudioEffects(exo.audioSessionId)
        }

        val listener = object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                ActivePlayerRegistry.setPlayerActive(isPlaying)
                if (isPlaying) scheduleControlsHide() else controlsHideJob?.cancel()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // Codec / size info is derived in updateCodecInfo()
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY, Player.STATE_BUFFERING -> {
                        _duration.value = player.duration.coerceAtLeast(0L)
                        _bufferedPosition.value = player.bufferedPosition
                        (player as? androidx.media3.exoplayer.ExoPlayer)?.let { exo ->
                            bindAudioEffects(exo.audioSessionId)
                        }
                        updateCodecInfo(player)
                    }
                    Player.STATE_ENDED -> { /* handled in service */ }
                    else -> {}
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                _currentPosition.value = newPosition.positionMs
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentPlaylistIndex.value = player.currentMediaItemIndex
                mediaItem?.localConfiguration?.uri?.path?.let { path ->
                    currentMediaPath = path
                    loadBookmarksForPath(path)
                }
                updateCodecInfo(player)
                extractChapters(player)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                _currentPosition.value  = player.currentPosition
                _bufferedPosition.value = player.bufferedPosition
                _duration.value         = player.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error encountered: ${error.errorCodeName}", error)
                if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                ) {
                    Log.i(TAG, "ExoPlayer decoder unsupported. Engaging LibVLC fallback player.")
                    _useVlcFallback.value = true
                } else {
                    val currentPos = player.currentPosition
                    player.prepare()
                    player.seekTo(currentPos)
                    player.play()
                }
            }
        }
        playerListener = listener
        player.addListener(listener)

        // Kick off A-B repeat enforcement
        startAbRepeatChecker()
    }

    fun bindAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return
        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
            }
            applyCurrentEqualizer()
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer init error: ${e.message}")
        }

        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = true
            }
            applyVolume(_volumeBoostPercent.value)
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer init error: ${e.message}")
        }
    }

    private fun applyCurrentEqualizer() {
        viewModelScope.launch {
            val s = settings.settingsFlow.first()
            try {
                val array = org.json.JSONArray(s.eqBandGainsJson)
                val gains = IntArray(array.length()) { i -> array.getInt(i) }
                equalizer?.let { eq ->
                    val bands = eq.numberOfBands.toInt()
                    val minLevel = eq.bandLevelRange[0]
                    val maxLevel = eq.bandLevelRange[1]
                    for (i in 0 until minOf(bands, gains.size)) {
                        eq.setBandLevel(i.toShort(), gains[i].coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort())
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Media loading
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun loadMedia(path: String, startPositionMs: Long = 0L) {
        currentMediaPath = path
        _useVlcFallback.value = false
        val uri = if (path.startsWith("content://") || path.startsWith("vault://") || path.startsWith("http://") || path.startsWith("https://")) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }
        _currentUri.value = uri

        if (uri.scheme == "vault") {
            try {
                val factory = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                    vaultManager.getEncryptedDataSourceFactory()
                )
                val mediaItem = MediaItem.Builder().setUri(uri).build()
                val mediaSource = factory.createMediaSource(mediaItem)
                (player as? androidx.media3.exoplayer.ExoPlayer)?.let { exo ->
                    exo.setMediaSource(mediaSource, startPositionMs)
                    exo.prepare()
                    exo.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing encrypted vault media: ${e.message}", e)
            }
        } else {
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .build()
            player?.run {
                setMediaItem(mediaItem, startPositionMs)
                prepare()
                play()
            }
        }
        loadBookmarksForPath(path)
    }

    fun loadPlaylist(paths: List<String>, startIndex: Int = 0) {
        val items = paths.map { path ->
            MediaItem.Builder().setUri(Uri.fromFile(File(path))).build()
        }
        _playlistItems.value      = items
        _currentPlaylistIndex.value = startIndex
        player?.run {
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
        if (paths.isNotEmpty()) currentMediaPath = paths[startIndex.coerceIn(paths.indices)]
    }

    fun playNext() {
        val items = _playlistItems.value
        if (items.isEmpty()) return
        val next = (_currentPlaylistIndex.value + 1) % items.size
        _currentPlaylistIndex.value = next
        player?.run {
            seekTo(next, 0L)
            play()
        }
    }

    fun playPrevious() {
        val items = _playlistItems.value
        if (items.isEmpty()) return
        val prev = (_currentPlaylistIndex.value - 1 + items.size) % items.size
        _currentPlaylistIndex.value = prev
        player?.run {
            seekTo(prev, 0L)
            play()
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Playback controls
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun togglePlayPause() {
        player?.run {
            if (isPlaying) pause() else play()
        }
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms.coerceAtLeast(0L))
    }

    fun seekRelativePercent(pct: Float) {
        val dur = _duration.value
        if (dur <= 0L) return
        val target = (pct * dur).toLong().coerceIn(0L, dur)
        player?.seekTo(target)
    }

    fun adjustSpeed(delta: Float) {
        val newSpeed = (_playbackSpeed.value + delta).coerceIn(0.1f, 8.0f)
        setSpeed(newSpeed)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.1f, 8.0f)
        _playbackSpeed.value = clamped
        player?.setPlaybackSpeed(clamped)
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Volume controls
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun increaseVolume() {
        val next = (_volumeBoostPercent.value + 10f).coerceAtMost(200f)
        _volumeBoostPercent.value = next
        applyVolume(next)
    }

    fun decreaseVolume() {
        val next = (_volumeBoostPercent.value - 10f).coerceAtLeast(0f)
        _volumeBoostPercent.value = next
        applyVolume(next)
    }

    private fun applyVolume(boostPercent: Float) {
        if (_isMuted.value) return
        if (boostPercent <= 100f) {
            player?.volume = boostPercent / 100f
            try { loudnessEnhancer?.setTargetGain(0) } catch (_: Exception) {}
        } else {
            player?.volume = 1.0f
            val gainMb = ((boostPercent - 100f) * 30f).toInt()
            try { loudnessEnhancer?.setTargetGain(gainMb) } catch (_: Exception) {}
        }
        viewModelScope.launch {
            settings.setVolumeBoostPercent(boostPercent)
        }
    }

    fun setVolumeBoostPercent(percent: Float) {
        val clamped = percent.coerceIn(0f, 200f)
        _volumeBoostPercent.value = clamped
        applyVolume(clamped)
    }

    fun toggleMute() {
        _isMuted.update { current ->
            val muted = !current
            player?.volume = if (muted) 0f else (_volumeBoostPercent.value / 100f).coerceIn(0f, 1f)
            muted
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Video transform controls
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private val aspectRatioCycle = listOf(
        "FIT", "FILL", "STRETCH", "16_9", "4_3", "21_9", "CENTER_CROP"
    )

    fun cycleAspectRatio() {
        val current = _aspectRatioMode.value
        val idx     = aspectRatioCycle.indexOf(current)
        val next    = aspectRatioCycle[(idx + 1) % aspectRatioCycle.size]
        _aspectRatioMode.value = next
    }

    private val cropModeCycle = listOf("NONE", "CROP_16_9", "CROP_4_3", "CROP_1_1")

    fun cycleCropMode() {
        val current = _cropMode.value
        val idx     = cropModeCycle.indexOf(current)
        _cropMode.value = cropModeCycle[(idx + 1) % cropModeCycle.size]
    }

    fun rotateVideo() {
        _videoRotationDegrees.value = (_videoRotationDegrees.value + 90) % 360
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // UI controls
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun toggleScreenLock() {
        _screenLocked.update { !it }
    }

    fun toggleCodecHud() {
        _showCodecHud.update { !it }
    }

    fun toggleControls() {
        val next = !_showControls.value
        _showControls.value = next
        if (next && _isPlaying.value) {
            scheduleControlsHide()
        } else {
            controlsHideJob?.cancel()
        }
    }

    fun showControlsTemporarily(delayMs: Long = 3_500L) {
        _showControls.value = true
        if (_isPlaying.value) {
            scheduleControlsHide(delayMs)
        }
    }

    fun hideControls() {
        controlsHideJob?.cancel()
        _showControls.value = false
    }

    fun scheduleControlsHide(delayMs: Long = 3_500L) {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(delayMs)
            _showControls.value = false
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // A-B repeat
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun setAbRepeatA() {
        _abRepeatAMs.value = player?.currentPosition ?: return
    }

    fun setAbRepeatB() {
        _abRepeatBMs.value = player?.currentPosition ?: return
    }

    fun clearAbRepeat() {
        _abRepeatAMs.value = -1L
        _abRepeatBMs.value = -1L
    }

    private fun startAbRepeatChecker() {
        abRepeatJob?.cancel()
        abRepeatJob = viewModelScope.launch {
            while (true) {
                delay(250)
                val a = _abRepeatAMs.value
                val b = _abRepeatBMs.value
                if (a >= 0 && b > a) {
                    val pos = player?.currentPosition ?: continue
                    if (pos >= b) {
                        player?.seekTo(a)
                    }
                }
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Bookmarks
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun addBookmark(label: String) {
        val path     = currentMediaPath ?: return
        val position = player?.currentPosition ?: 0L
        viewModelScope.launch(Dispatchers.IO) {
            val mediaItem = db.mediaItemDao().getByPath(path)
            val mediaId = mediaItem?.id ?: 0L
            val bookmark = Bookmark(
                mediaItemId  = mediaId,
                positionMs   = position,
                label        = label,
                createdAt    = System.currentTimeMillis(),
            )
            db.bookmarkDao().insert(bookmark)
            loadBookmarksForPath(path)
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bookmarkDao().deleteById(id)
            currentMediaPath?.let { loadBookmarksForPath(it) }
        }
    }

    private fun loadBookmarksForPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val bookmarks = db.bookmarkDao().getByPath(path)
            _currentBookmarks.value = bookmarks
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Screenshot / GIF capture
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun captureScreenshot() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Retrieve current video frame via ExoPlayer's frame extraction API (Media3 1.3+).
                // We build a temporary extractor for the current URI.
                val path = currentMediaPath ?: return@launch
                val uri  = Uri.fromFile(File(path))
                val position = _currentPosition.value

                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(path)
                val bitmap: Bitmap? = retriever.getFrameAtTime(
                    position * 1_000L, // Âµs
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()

                bitmap ?: return@launch

                val ts        = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName  = "Screenshot_$ts.png"
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
                    put(android.provider.MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartGallery")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val targetUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (targetUri != null) {
                    context.contentResolver.openOutputStream(targetUri)?.use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(targetUri, values, null, null)
                    }
                }
                Log.d(TAG, "Screenshot saved to MediaStore: $fileName")
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot failed", e)
            }
        }
    }

    fun startGifRecording() {
        val path = currentMediaPath ?: return
        _gifMakerState.value = GifMakerState.Recording(0L)

        val startPositionMs = _currentPosition.value
        val startSecs = (startPositionMs / 1000L).coerceAtLeast(0L)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val tempFile = File(context.cacheDir, "temp_gif_$ts.gif")

                val ffmpegDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
                val ffmpegBin = File(ffmpegDir, "bin/ffmpeg").takeIf { it.exists() }
                    ?: File(ffmpegDir, "ffmpeg").takeIf { it.exists() }
                    ?: File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

                val cmd = mutableListOf(
                    ffmpegBin.absolutePath,
                    "-y",
                    "-ss", startSecs.toString(),
                    "-t", "5",
                    "-i", path,
                    "-vf", "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
                    "-loop", "0",
                    tempFile.absolutePath
                )

                val pb = ProcessBuilder(cmd)
                pb.redirectErrorStream(true)
                val process = pb.start()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { Log.d(TAG, "FFmpeg GIF: $it") }
                }
                val exitCode = process.waitFor()

                if (exitCode == 0 && tempFile.exists() && tempFile.length() > 0) {
                    val outDisplayName = "GIF_$ts.gif"
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, outDisplayName)
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/gif")
                        put(android.provider.MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
                        put(android.provider.MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartGallery/GIFs")
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val targetUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (targetUri != null) {
                        context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                            tempFile.inputStream().use { it.copyTo(outStream) }
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                            context.contentResolver.update(targetUri, values, null, null)
                        }
                    }
                    tempFile.delete()
                    val publicPath = File(
                        File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "SmartGallery/GIFs"),
                        outDisplayName
                    ).absolutePath

                    _gifMakerState.value = GifMakerState.Done(publicPath)
                    Log.d(TAG, "5-Second Animated GIF saved: $publicPath")
                } else {
                    _gifMakerState.value = GifMakerState.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "GIF recording failed", e)
                _gifMakerState.value = GifMakerState.Idle
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Position persistence
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun savePlaybackPosition() {
        val path = currentMediaPath ?: return
        val pos  = player?.currentPosition ?: 0L
        viewModelScope.launch(Dispatchers.IO) {
            db.playbackStateDao().updatePosition(path, pos, System.currentTimeMillis())
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Navigation / PiP / background audio
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun handleBack() {
        savePlaybackPosition()
        viewModelScope.launch { _backEvent.emit(Unit) }
    }

    fun enterPip() {
        _isInPip.value = true
        viewModelScope.launch { _pipEvent.emit(true) }
    }

    fun exitPip() {
        _isInPip.value = false
        viewModelScope.launch { _pipEvent.emit(false) }
    }

    fun toggleBackgroundAudio() {
        _isBackgroundAudio.update { !it }
        viewModelScope.launch {
            settings.setBackgroundAudioOnScreenOff(_isBackgroundAudio.value)
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Chapter extraction
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun extractChapters(player: Player) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val timeline = player.currentTimeline
                if (timeline.isEmpty) return@launch

                val window = Timeline.Window()
                timeline.getWindow(0, window)

                // Media3 stores chapter metadata in MediaMetadata.extras or via timed metadata;
                // fall back to empty list when unavailable at the MediaItem level.
                val mediaMetadata = window.mediaItem.mediaMetadata
                val chaptersJson  = mediaMetadata.extras?.getString("chapters")

                val chapters = mutableListOf<Chapter>()
                if (!chaptersJson.isNullOrBlank()) {
                    try {
                        val arr = org.json.JSONArray(chaptersJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            chapters.add(
                                Chapter(
                                    titleMs = obj.getLong("positionMs"),
                                    label   = obj.getString("title"),
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse chapters JSON", e)
                    }
                }

                // Also attempt to read timed metadata from MediaMetadata.title/artist
                // as a named-chapter hint (heuristic for Matroska chapter titles).
                _currentChapters.value = chapters
            } catch (e: Exception) {
                Log.w(TAG, "Chapter extraction failed", e)
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Codec HUD info
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun updateCodecInfo(player: Player) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Access track info via Player.getCurrentTracks()
                val tracks = player.currentTracks
                val sb     = StringBuilder()

                for (group in tracks.groups) {
                    if (!group.isSelected) continue
                    val format = group.getTrackFormat(
                        (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: continue
                    )

                    when {
                        format.sampleMimeType?.startsWith("video/") == true -> {
                            // Codec name
                            val codec = format.codecs
                                ?: format.sampleMimeType?.removePrefix("video/")?.uppercase()
                                ?: "?"
                            // Resolution
                            val w = format.width.takeIf { it > 0 } ?: 0
                            val h = format.height.takeIf { it > 0 } ?: 0
                            // Frame rate
                            val fps = if (format.frameRate > 0) "%.2f".format(format.frameRate) else "?"
                            // Bitrate
                            val bitrateMbps = if (format.bitrate > 0)
                                "${"%.1f".format(format.bitrate / 1_000_000f)}Mbps"
                            else "?"
                            // HW / SW decode â€” derived from DecoderCounters (not directly exposed
                            // in Player API; we mark as HW when codec name does not contain "sw")
                            val hwTag = if (codec.contains("sw", ignoreCase = true)) "SW" else "HW"
                            if (sb.isNotEmpty()) sb.append(" | ")
                            sb.append("$codec | ${w}Ã—${h} | ${fps}fps | $hwTag | $bitrateMbps")
                        }

                        format.sampleMimeType?.startsWith("audio/") == true -> {
                            val aCodec = format.codecs
                                ?: format.sampleMimeType?.removePrefix("audio/")?.uppercase()
                                ?: "?"
                            val ch = format.channelCount.takeIf { it > 0 }?.let { "${it}ch" } ?: ""
                            val sr = format.sampleRate.takeIf { it > 0 }?.let { "${it / 1000}kHz" } ?: ""
                            if (sb.isNotEmpty()) sb.append(" | ")
                            sb.append("$aCodec $ch $sr".trim())
                        }
                    }
                }

                _codecInfo.value = sb.toString().ifBlank { "No track info" }
            } catch (e: Exception) {
                Log.w(TAG, "updateCodecInfo failed", e)
                _codecInfo.value = ""
            }
        }
    }

    // â”€â”€ Equalizer & Sleep timer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    val settingsFlow = settings.settingsFlow

    fun setEqPreset(preset: String) {
        viewModelScope.launch {
            settings.setEqPreset(preset)
        }
    }

    fun setEqBandGains(gains: IntArray) {
        viewModelScope.launch {
            settings.setEqBandGains(gains)
            equalizer?.let { eq ->
                val bands = eq.numberOfBands.toInt()
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                for (i in 0 until minOf(bands, gains.size)) {
                    try {
                        eq.setBandLevel(i.toShort(), gains[i].coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort())
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun setEqPreamp(preampMb: Int) {
        viewModelScope.launch {
            settings.setEqPreampMb(preampMb)
        }
    }

    fun setEqReverb(reverbPreset: Int) {
        viewModelScope.launch {
            settings.setEqReverbPreset(reverbPreset)
        }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        viewModelScope.launch {
            settings.setSleepTimerMinutes(minutes)
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Cleanup
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    override fun onCleared() {
        ActivePlayerRegistry.setPlayerActive(false)
        playerListener?.let { player?.removeListener(it) }
        try { equalizer?.release() } catch (_: Exception) {}
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        equalizer = null
        loudnessEnhancer = null
        abRepeatJob?.cancel()
        controlsHideJob?.cancel()
        super.onCleared()
    }
}

