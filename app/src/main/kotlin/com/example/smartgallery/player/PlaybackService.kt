package com.example.smartgallery.player

import android.content.Intent
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Custom session commands
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private const val CMD_SET_SPEED            = "SET_SPEED"
private const val CMD_SET_VOLUME_BOOST     = "SET_VOLUME_BOOST"
private const val CMD_SET_EQ_BAND          = "SET_EQ_BAND"
private const val CMD_SET_SLEEP_TIMER      = "SET_SLEEP_TIMER"
private const val CMD_SAVE_POSITION        = "SAVE_POSITION"
private const val CMD_TOGGLE_BG_AUDIO      = "TOGGLE_BACKGROUND_AUDIO"

private const val TAG = "PlaybackService"

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    // â”€â”€ Core playback components â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var loudnessEnhancer: LoudnessEnhancer
    private lateinit var equalizer: Equalizer
    private var virtualizer: Virtualizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    // â”€â”€ Sleep timer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private var sleepTimerJob: Job? = null

    // â”€â”€ Coroutine scope â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // â”€â”€ Injected dependencies â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var galleryDatabase: GalleryDatabase
    @Inject lateinit var profiler: com.example.smartgallery.profiler.DeviceCapabilityProfiler

    // â”€â”€ Audio focus â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN           -> player.play()
            AudioManager.AUDIOFOCUS_LOSS           -> player.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.volume = 0.3f
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Lifecycle
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    override fun onCreate() {
        super.onCreate()

        // Build ExoPlayer with extension (FFmpeg) renderer preference
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)

        // Track selector with adaptive audio/video selection
        val trackSelector = DefaultTrackSelector(this, AdaptiveTrackSelection.Factory()).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(false)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
            )
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                profiler.exoPlayerMinBufferMs,
                profiler.exoPlayerMaxBufferMs,
                profiler.exoPlayerBufferForPlaybackMs,
                profiler.exoPlayerBufferForPlaybackAfterRebufferMs
            )
            .build()

        // Build player
        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Build MediaSession
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallbackImpl())
            .build()

        // Audio effects (must be set up after player creation for valid audioSessionId)
        setupAudioEffects()

        // Load & apply saved EQ from DataStore
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val bandsJson = settings.eqBandGainsJson
            val preampMb  = settings.eqPreampMb
            val bandGains = parseBandGains(bandsJson)
            applyEqualizer(bandGains, preampMb)
            applyLoudnessBoost(settings.volumeBoostPercent)
            applyChannelMode(settings.channelMode)
        }

        // Audio focus
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AndroidAudioAttributes.Builder()
                    .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                    .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        audioManager.requestAudioFocus(audioFocusRequest)

        // Player event listener
        player.addListener(PlayerListenerImpl())
    }

    private fun setupAudioEffects() {
        val sessionId = player.audioSessionId
        loudnessEnhancer = LoudnessEnhancer(sessionId)
        loudnessEnhancer.setEnabled(false)

        equalizer = Equalizer(0, sessionId)
        equalizer.setEnabled(true)
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // MediaSession.Callback implementation
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private inner class MediaSessionCallbackImpl : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val customCommands = listOf(
                CMD_SET_SPEED,
                CMD_SET_VOLUME_BOOST,
                CMD_SET_EQ_BAND,
                CMD_SET_SLEEP_TIMER,
                CMD_SAVE_POSITION,
                CMD_TOGGLE_BG_AUDIO,
            ).map { SessionCommand(it, Bundle.EMPTY) }

            val builder = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            customCommands.forEach { builder.add(it) }
            val sessionCommands = builder.build()

            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture(mediaItems)

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {

                CMD_SET_SPEED -> {
                    val speed = args.getFloat("speed", 1.0f)
                    player.setPlaybackSpeed(speed)
                }

                CMD_SET_VOLUME_BOOST -> {
                    val percent = args.getFloat("percent", 100f)
                    applyLoudnessBoost(percent)
                }

                CMD_SET_EQ_BAND -> {
                    val band    = args.getInt("band", 0)
                    val gainMb  = args.getInt("gainMb", 0)
                    val preamp  = args.getInt("preampMb", 0)
                    // Update single band; retrieve current state from equalizer
                    val current = IntArray(equalizer.numberOfBands.toInt()) { i ->
                        equalizer.getBandLevel(i.toShort()).toInt()
                    }
                    if (band in current.indices) current[band] = gainMb
                    applyEqualizer(current, preamp)
                    // Persist updated bands
                    scope.launch {
                        settingsRepository.setEqBandGains(current)
                        settingsRepository.setEqPreampMb(preamp)
                    }
                }

                CMD_SET_SLEEP_TIMER -> {
                    val minutes = args.getInt("minutes", -1)
                    startSleepTimer(minutes)
                }

                CMD_SAVE_POSITION -> {
                    saveCurrentPosition()
                }

                CMD_TOGGLE_BG_AUDIO -> {
                    val enable = args.getBoolean("enable", false)
                    if (!enable) {
                        // Re-request audio focus and ensure video surface is restored externally
                        audioManager.requestAudioFocus(audioFocusRequest)
                    }
                    Log.d(TAG, "Background audio toggled: $enable")
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Player.Listener implementation
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private inner class PlayerListenerImpl : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                scope.launch {
                    val action = settingsRepository.settingsFlow.first().playlistEndAction
                    when (action) {
                        "REPEAT_ONE"  -> { player.seekTo(0); player.play() }
                        "REPEAT_ALL"  -> {
                            val next = (player.currentMediaItemIndex + 1) % player.mediaItemCount
                            player.seekTo(next, 0L)
                            player.play()
                        }
                        "PLAY_NEXT"   -> {
                            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                            else player.pause()
                        }
                        else          -> player.pause() // STOP / default
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.errorCodeName} â€” ${error.message}", error)
            // Attempt software fallback by rebuilding renderer with software-only mode
            val currentPosition = player.currentPosition
            val currentIndex    = player.currentMediaItemIndex
            val currentItems    = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            val wasPlaying      = player.isPlaying

            scope.launch {
                try {
                    val softRenderersFactory = DefaultRenderersFactory(this@PlaybackService)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                    val newPlayer = ExoPlayer.Builder(this@PlaybackService, softRenderersFactory)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                                .build(),
                            true
                        )
                        .build()
                    newPlayer.setMediaItems(currentItems, currentIndex, currentPosition)
                    newPlayer.addListener(PlayerListenerImpl())
                    if (wasPlaying) newPlayer.prepare().also { newPlayer.play() }
                    else newPlayer.prepare()

                    mediaSession.player.release()
                    // Re-attach to session
                    mediaSession.player = newPlayer
                    player.release()
                    player = newPlayer

                    // Re-attach audio effects
                    loudnessEnhancer.release()
                    equalizer.release()
                    setupAudioEffects()
                    Log.d(TAG, "Switched to software renderer after hardware error.")
                } catch (e: Exception) {
                    Log.e(TAG, "Software fallback failed", e)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            saveCurrentPosition()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem ?: return
            val path = mediaItem.localConfiguration?.uri?.path ?: return
            scope.launch(Dispatchers.IO) {
                // Load saved playback state for the new item
                val state = galleryDatabase.playbackStateDao().getByPath(path)
                if (state != null) {
                    scope.launch(Dispatchers.Main) {
                        player.seekTo(state.positionMs)
                    }
                }
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Audio effects helpers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Apply 10-band equalizer levels.
     * [bandGainsMb] â€“ per-band gain in millibels (index 0 = lowest band).
     * [preampMb]    â€“ pre-amplification offset added to every band.
     */
    fun applyEqualizer(bandGainsMb: IntArray, preampMb: Int) {
        val numBands = equalizer.numberOfBands.toInt()
        for (i in 0 until numBands) {
            val gain = if (i < bandGainsMb.size) bandGainsMb[i] + preampMb else preampMb
            val clamped = gain.coerceIn(
                equalizer.getBandLevelRange()[0].toInt(),
                equalizer.getBandLevelRange()[1].toInt()
            )
            equalizer.setBandLevel(i.toShort(), clamped.toShort())
        }
    }

    /**
     * Apply loudness / volume boost.
     * [percent] â€“ 0..100 â†’ attenuate/normal; 101..200 â†’ LoudnessEnhancer boost.
     */
    fun applyLoudnessBoost(percent: Float) {
        if (percent <= 100f) {
            loudnessEnhancer.setEnabled(false)
            player.volume = (percent / 100f).coerceIn(0f, 1f)
        } else {
            player.volume = 1.0f
            // Convert extra percentage points to millibels (each extra 1% â†’ 10 mB)
            val boostMb = ((percent - 100f) * 10f).toInt()
            loudnessEnhancer.setTargetGain(boostMb)
            loudnessEnhancer.setEnabled(true)
        }
    }

    /**
     * Schedule or cancel the sleep timer with smooth 30-second audio fade-out.
     * [minutes] <= 0 cancels an active timer; any positive value sets a new one.
     */
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes <= 0) return
        sleepTimerJob = scope.launch {
            val totalMs = minutes * 60_000L
            val fadeDurationMs = 30_000L
            val initialWaitMs = (totalMs - fadeDurationMs).coerceAtLeast(0L)

            if (initialWaitMs > 0L) {
                delay(initialWaitMs)
            }

            // Smooth 30s volume fade out before pausing
            val startVolume = player.volume
            val steps = 30
            val stepDelay = (if (totalMs < fadeDurationMs) totalMs else fadeDurationMs) / steps
            for (step in 1..steps) {
                delay(stepDelay)
                val factor = (steps - step).toFloat() / steps.toFloat()
                player.volume = (startVolume * factor).coerceIn(0f, 1f)
            }

            player.pause()
            player.volume = startVolume // Restore volume setting after pausing
            Log.d(TAG, "Sleep timer fired with 30s audio fade-out — playback paused.")
        }
    }

    /**
     * Apply a channel audio mode using Android AudioEffects.
     */
    fun applyChannelMode(mode: String) {
        val sessionId = player.audioSessionId

        // Release existing channel-specific effects first
        virtualizer?.release()
        virtualizer = null
        dynamicsProcessing?.release()
        dynamicsProcessing = null

        when (mode.uppercase()) {
            "LEFT" -> {
                // Workaround: attenuate right channel by overriding AudioEffect parameters.
                // The standard AudioEffect API does not expose per-channel balance directly
                // on all devices, so we use the LoudnessEnhancer path with a custom
                // channel balance workaround: set right-channel coefficient close to 0.
                // On most hardware this is achieved via proprietary AudioEffect UUIDs;
                // here we use the available DynamicsProcessing with a channel-specific gain.
                val dpConfig = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, false, 0, false, 0, false, 0, false
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, sessionId, dpConfig).also { dp ->
                    // Mute the right channel (index 1) by setting a large negative input gain
                    dp.setInputGainAllChannelsTo(0f)
                    dp.setInputGainbyChannel(0, 0f)   // left: unity
                    dp.setInputGainbyChannel(1, -80f) // right: near-mute in dB
                    dp.setEnabled(true)
                }
            }

            "RIGHT" -> {
                val dpConfig = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, false, 0, false, 0, false, 0, false
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, sessionId, dpConfig).also { dp ->
                    dp.setInputGainAllChannelsTo(0f)
                    dp.setInputGainbyChannel(0, -80f) // left: near-mute
                    dp.setInputGainbyChannel(1, 0f)   // right: unity
                    dp.setEnabled(true)
                }
            }

            "MONO" -> {
                // Virtualizer at strength 0 effectively keeps stereo unchanged;
                // for true mono we rely on the renderer's downmix channel count setting.
                // Set virtualizer at minimum to prevent stereo widening.
                virtualizer = Virtualizer(0, sessionId).also { virt ->
                    virt.setStrength(0)
                    virt.setEnabled(false)
                }
            }

            "NIGHT" -> {
                // Boost mid-range (voice) frequencies and compress dynamics
                val dpConfig = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, 3, false, 0, false, 0, false
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, sessionId, dpConfig).also { dp ->
                    dp.setPreEqBandAllChannelsTo(0, DynamicsProcessing.EqBand(true, 1000f, 4f))
                    dp.setPreEqBandAllChannelsTo(1, DynamicsProcessing.EqBand(true, 4000f, -2f))
                    dp.setEnabled(true)
                }
            }

            "SURROUND" -> {
                virtualizer = Virtualizer(0, sessionId).also { virt ->
                    if (virt.strengthSupported) {
                        virt.setStrength(1000)
                    }
                    virt.setEnabled(true)
                }
            }

            else -> { /* STEREO â€” no additional effects; all released above */ }
        }
    }

    /**
     * Persist the current playback position to Room DB.
     */
    fun saveCurrentPosition() {
        player.currentMediaItem?.localConfiguration?.uri?.path?.let { path ->
            val positionMs = player.currentPosition
            scope.launch(Dispatchers.IO) {
                galleryDatabase.playbackStateDao()
                    .updatePosition(path, positionMs, System.currentTimeMillis())
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // MediaSessionService overrides
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onDestroy() {
        ActivePlayerRegistry.setPlayerActive(false)
        saveCurrentPosition()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        mediaSession.release()
        player.release()
        loudnessEnhancer.release()
        equalizer.release()
        virtualizer?.release()
        dynamicsProcessing?.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Utilities
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun parseBandGains(json: String): IntArray {
        return try {
            val arr = JSONArray(json)
            IntArray(arr.length()) { arr.getInt(it) }
        } catch (e: Exception) {
            IntArray(10) { 0 }
        }
    }
}

