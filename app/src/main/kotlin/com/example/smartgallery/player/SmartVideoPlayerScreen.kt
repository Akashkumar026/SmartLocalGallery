package com.example.smartgallery.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.example.smartgallery.data.db.Bookmark
import com.example.smartgallery.data.settings.AppSettings
import com.example.smartgallery.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Hilt EntryPoint for standalone or fallback injection
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerScreenEntryPoint {
    fun subtitleAndAudioController(): SubtitleAndAudioController
    fun playerKeyEventDispatcher(): PlayerKeyEventDispatcher
}

@Composable
fun rememberSubtitleAndAudioController(): SubtitleAndAudioController {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                PlayerScreenEntryPoint::class.java
            )
            entryPoint.subtitleAndAudioController()
        } catch (e: Exception) {
            val settings = SettingsRepository(context)
            SubtitleAndAudioController(settings)
        }
    }
}

@Composable
fun rememberPlayerKeyEventDispatcher(): PlayerKeyEventDispatcher {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                PlayerScreenEntryPoint::class.java
            )
            entryPoint.playerKeyEventDispatcher()
        } catch (e: Exception) {
            val settings = SettingsRepository(context)
            PlayerKeyEventDispatcher(settings)
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Supporting Models & Helpers
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

enum class ActiveGesture { NONE, BRIGHTNESS, VOLUME, SEEK }

data class OsdState(
    val icon: ImageVector,
    val title: String,
    val valueText: String = "",
    val progress: Float? = null,
    val alignment: Alignment = Alignment.Center
)

data class VideoColorFilters(
    val brightness: Float = 0f,   // -1.0 to 1.0
    val contrast: Float = 1f,     // 0.5 to 2.0
    val saturation: Float = 1f,   // 0.0 to 2.0
    val mirrorHorizontal: Boolean = false
)

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

fun setWindowBrightness(activity: Activity?, brightness: Float) {
    activity?.window?.let { window ->
        val lp = window.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
        window.attributes = lp
    }
}

fun getWindowBrightness(activity: Activity?): Float {
    val lp = activity?.window?.attributes
    val current = lp?.screenBrightness ?: -1f
    return if (current >= 0) current else 0.5f
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// COMPOSABLE: SmartVideoPlayerScreen
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartVideoPlayerScreen(
    navController: NavController,
    mediaPath: String,
    viewModel: SmartVideoPlayerViewModel = hiltViewModel(),
    subtitleController: SubtitleAndAudioController = rememberSubtitleAndAudioController()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val dispatcher = rememberPlayerKeyEventDispatcher()

    // â”€â”€ State Flows â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val bufferedPosition by viewModel.bufferedPosition.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val volumeBoostPercent by viewModel.volumeBoostPercent.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsStateWithLifecycle()
    val videoRotationDegrees by viewModel.videoRotationDegrees.collectAsStateWithLifecycle()
    val screenLocked by viewModel.screenLocked.collectAsStateWithLifecycle()
    val showCodecHud by viewModel.showCodecHud.collectAsStateWithLifecycle()
    val showControls by viewModel.showControls.collectAsStateWithLifecycle()
    val abRepeatAMs by viewModel.abRepeatAMs.collectAsStateWithLifecycle()
    val abRepeatBMs by viewModel.abRepeatBMs.collectAsStateWithLifecycle()
    val currentBookmarks by viewModel.currentBookmarks.collectAsStateWithLifecycle()
    val currentChapters by viewModel.currentChapters.collectAsStateWithLifecycle()
    val codecInfo by viewModel.codecInfo.collectAsStateWithLifecycle()
    val isInPip by viewModel.isInPip.collectAsStateWithLifecycle()
    val gifMakerState by viewModel.gifMakerState.collectAsStateWithLifecycle()
    val useVlcFallback by viewModel.useVlcFallback.collectAsStateWithLifecycle()
    val currentUri by viewModel.currentUri.collectAsStateWithLifecycle()

    // Subtitle & Audio state
    val currentSubtitleText by subtitleController.currentSubtitleText.collectAsStateWithLifecycle()
    val currentSecondarySubtitleText by subtitleController.currentSecondarySubtitleText.collectAsStateWithLifecycle()
    val subtitleStyle by subtitleController.subtitleStyle.collectAsStateWithLifecycle()
    val dualSubtitleEnabled by subtitleController.dualSubtitleEnabled.collectAsStateWithLifecycle()
    val availableSubtitles by subtitleController.availableSubtitles.collectAsStateWithLifecycle()
    val availableAudioTracks by subtitleController.availableAudioTracks.collectAsStateWithLifecycle()
    val primarySubtitleIndex by subtitleController.primarySubtitleIndex.collectAsStateWithLifecycle()
    val activeAudioTrackIndex by subtitleController.activeAudioTrackIndex.collectAsStateWithLifecycle()
    val subtitleSync by subtitleController.subtitleSync.collectAsStateWithLifecycle()
    val audioDelayMs by subtitleController.audioDelayMs.collectAsStateWithLifecycle()
    val channelMode by subtitleController.channelMode.collectAsStateWithLifecycle()

    // App Settings
    val appSettings by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    // â”€â”€ Dialog & Sheet Visibility â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var showSubtitleAudioSheet by remember { mutableStateOf(false) }
    var subtitleAudioSheetTab by remember { mutableIntStateOf(0) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showFiltersDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showId3EditorSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPlayerSettingsSheet by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // â”€â”€ Video Color Filters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var colorFilters by remember { mutableStateOf(VideoColorFilters()) }

    // â”€â”€ Gesture States â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }
    var panOffsetY by remember { mutableFloatStateOf(0.0f) }

    var localBrightness by remember { mutableFloatStateOf(getWindowBrightness(activity)) }
    var isLongPressFastForward by remember { mutableStateOf(false) }
    var speedBeforeLongPress by remember { mutableFloatStateOf(1.0f) }

    // Double tap ripples: left = false, right = true; count = seek delta
    var doubleTapSeekState by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastTapX by remember { mutableFloatStateOf(0f) }
    var activeGesture by remember { mutableStateOf(ActiveGesture.NONE) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var seekPreviewMs by remember { mutableLongStateOf(0L) }

    // OSD state
    var osdState by remember { mutableStateOf<OsdState?>(null) }
    var osdJob by remember { mutableStateOf<Job?>(null) }

    fun triggerOsd(state: OsdState, durationMs: Long = 1500L) {
        osdJob?.cancel()
        osdState = state
        osdJob = scope.launch {
            delay(durationMs)
            osdState = null
        }
    }

    // â”€â”€ ExoPlayer Setup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    val exoPlayer = remember(context) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                8_000,
                30_000,
                1_000,
                1_500
            )
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setSeekBackIncrementMs(appSettings.doubleTapSeekSecs * 1000L)
            .setSeekForwardIncrementMs(appSettings.doubleTapSeekSecs * 1000L)
            .build()
    }

    DisposableEffect(exoPlayer, mediaPath) {
        viewModel.initPlayer(exoPlayer)
        viewModel.loadMedia(mediaPath)
        subtitleController.loadTracksFromPlayer(exoPlayer)

        ActivePlayerRegistry.isPlayerActive = true
        ActivePlayerRegistry.activePlayer = exoPlayer
        ActivePlayerRegistry.activeViewModel = viewModel
        ActivePlayerRegistry.activeSubController = subtitleController

        val playerListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                subtitleController.loadTracksFromPlayer(exoPlayer)
            }
            override fun onCues(cueGroup: CueGroup) {
                val cuesText = cueGroup.cues.joinToString("\n") { it.text?.toString() ?: "" }
                subtitleController.setCurrentSubtitleText(cuesText)
            }
        }
        exoPlayer.addListener(playerListener)

        onDispose {
            ActivePlayerRegistry.isPlayerActive = false
            ActivePlayerRegistry.activePlayer = null
            ActivePlayerRegistry.activeViewModel = null
            ActivePlayerRegistry.activeSubController = null
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            exoPlayer.removeListener(playerListener)
            viewModel.savePlaybackPosition()
            exoPlayer.release()
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlin.runCatching { focusRequester.requestFocus() }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun toggleScreenOrientation() {
        activity?.let { act ->
            act.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.orientationToggleEvent.collect {
            toggleScreenOrientation()
        }
    }

    LaunchedEffect(configuration.orientation) {
        viewModel.showControlsTemporarily(3500L)
    }

    // Handle PiP events
    LaunchedEffect(Unit) {
        viewModel.pipEvent.collect { enter ->
            if (enter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val aspectRatio = Rational(16, 9)
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                activity?.enterPictureInPictureMode(params)
            }
        }
    }

    // Handle back event
    LaunchedEffect(Unit) {
        viewModel.backEvent.collect {
            navController.popBackStack()
        }
    }

    // Handle system back
    BackHandler {
        if (screenLocked) {
            triggerOsd(
                OsdState(
                    icon = Icons.Default.Lock,
                    title = "Screen Locked",
                    valueText = "Swipe up or tap unlock to use controls"
                )
            )
        } else {
            viewModel.handleBack()
        }
    }

    // Video file name
    val videoFileName = remember(mediaPath) {
        File(mediaPath).name
    }
    val resolutionBadge = remember(codecInfo) {
        when {
            codecInfo.contains("3840", ignoreCase = true) || codecInfo.contains("4K", ignoreCase = true) -> "4K UHD"
            codecInfo.contains("1920", ignoreCase = true) || codecInfo.contains("1080", ignoreCase = true) -> "1080p HEVC"
            codecInfo.contains("1280", ignoreCase = true) || codecInfo.contains("720", ignoreCase = true) -> "720p HD"
            codecInfo.isNotBlank() -> codecInfo.substringBefore("|").trim()
            else -> "1080p"
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // ROOT BOX
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .playerKeyEvents(
                dispatcher = dispatcher,
                player = exoPlayer,
                controller = subtitleController,
                viewModel = viewModel
            )
    ) {
        // ── 1. ExoPlayer AndroidView Surface ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale * (if (colorFilters.mirrorHorizontal) -1f else 1f)
                    scaleY = zoomScale
                    translationX = panOffsetX
                    translationY = panOffsetY
                }
        ) {
            if (useVlcFallback && currentUri != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val vlcLayout = org.videolan.libvlc.util.VLCVideoLayout(ctx)
                        val args = arrayListOf("-vvv")
                        val libVlc = org.videolan.libvlc.LibVLC(ctx, args)
                        val vlcPlayer = org.videolan.libvlc.MediaPlayer(libVlc)
                        vlcPlayer.attachViews(vlcLayout, null, false, false)
                        val media = org.videolan.libvlc.Media(libVlc, currentUri!!)
                        vlcPlayer.media = media
                        media.release()
                        vlcPlayer.play()
                        vlcLayout.tag = Pair(libVlc, vlcPlayer)
                        vlcLayout
                    },
                    update = { _ -> },
                    onRelease = { view ->
                        val pair = view.tag as? Pair<*, *>
                        (pair?.second as? org.videolan.libvlc.MediaPlayer)?.let { vlcPlayer ->
                            runCatching {
                                vlcPlayer.stop()
                                vlcPlayer.detachViews()
                                vlcPlayer.release()
                            }
                        }
                        (pair?.first as? org.videolan.libvlc.LibVLC)?.let { libVlc ->
                            runCatching { libVlc.release() }
                        }
                    }
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
                            useController = false
                            resizeMode = when (aspectRatioMode) {
                                "FIT" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                "16_9" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                "4_3" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                "21_9" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                "CENTER_CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                    },
                    update = { playerView ->
                        if (playerView.player !== exoPlayer) {
                            playerView.player = exoPlayer
                        }
                        playerView.resizeMode = when (aspectRatioMode) {
                            "FIT" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            "16_9" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            "4_3" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            "21_9" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            "CENTER_CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                )
            }

            // Video Color Filter Overlay (simulate brightness, contrast, saturation)
            if (colorFilters.brightness != 0f || colorFilters.contrast != 1f || colorFilters.saturation != 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (colorFilters.brightness < 0) {
                                Color.Black.copy(alpha = -colorFilters.brightness * 0.7f)
                            } else {
                                Color.White.copy(alpha = colorFilters.brightness * 0.4f)
                            }
                        )
                )
            }
        }

        // â”€â”€ 2. SUBTITLE OVERLAY â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val fontFamily = when (subtitleStyle.fontFamily.uppercase()) {
            "SERIF" -> FontFamily.Serif
            "MONO" -> FontFamily.Monospace
            else -> FontFamily.Default
        }
        val fontWeight = if (subtitleStyle.fontFamily.equals("BOLD", ignoreCase = true)) {
            FontWeight.Bold
        } else {
            FontWeight.Normal
        }

        // Primary Subtitle (bottom)
        if (currentSubtitleText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = (subtitleStyle.bottomMarginDp + (if (showControls) 90 else 24)).dp,
                        start = 32.dp,
                        end = 32.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = subtitleStyle.bgOpacity),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // Outline stroke layer
                    if (subtitleStyle.outlineWidth > 0f) {
                        Text(
                            text = currentSubtitleText,
                            style = TextStyle(
                                fontSize = subtitleStyle.fontSizeSp.sp,
                                fontFamily = fontFamily,
                                fontWeight = fontWeight,
                                textAlign = TextAlign.Center,
                                drawStyle = Stroke(
                                    width = subtitleStyle.outlineWidth * 2f,
                                    join = StrokeJoin.Round
                                ),
                                color = Color(subtitleStyle.outlineColorArgb)
                            )
                        )
                    }
                    // Main filled text layer with shadow
                    Text(
                        text = currentSubtitleText,
                        style = TextStyle(
                            fontSize = subtitleStyle.fontSizeSp.sp,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            textAlign = TextAlign.Center,
                            color = Color(subtitleStyle.textColorArgb),
                            shadow = if (subtitleStyle.dropShadow) {
                                Shadow(
                                    color = Color.Black.copy(alpha = 0.9f),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            } else null
                        )
                    )
                }
            }
        }

        // Dual Subtitle (Secondary subtitle at top)
        if (dualSubtitleEnabled && currentSecondarySubtitleText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = (if (showControls) 80 else 32).dp, start = 32.dp, end = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = subtitleStyle.bgOpacity),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = currentSecondarySubtitleText,
                        style = TextStyle(
                            fontSize = (subtitleStyle.fontSizeSp * 0.85f).sp,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            textAlign = TextAlign.Center,
                            color = Color(0xFFFFEB3B),
                            shadow = Shadow(
                                color = Color.Black,
                                offset = Offset(2f, 2f),
                                blurRadius = 3f
                            )
                        )
                    )
                }
            }
        }

        // â”€â”€ 3. GESTURE LAYER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (!screenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(screenLocked) {
                        val touchSlop = viewConfiguration.touchSlop
                        var lastTapTime = 0L
                        var lastTapPos = Offset.Zero
                        var singleTapJob: Job? = null

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            val startPos = down.position
                            val screenWidth = size.width.toFloat()
                            val screenHeight = size.height.toFloat()

                            var isMultiTouch = false
                            var isDrag = false
                            var isLongPressActive = false
                            var isHorizontalScrub = false
                            var isVerticalBrightness = false
                            var isVerticalVolume = false
                            var scrubTargetMs = exoPlayer.currentPosition
                            val durationMs = duration.coerceAtLeast(1L)
                            val initialPositionMs = exoPlayer.currentPosition

                            val isDoubleTapCandidate = (downTime - lastTapTime < 350L) &&
                                ((startPos - lastTapPos).getDistance() < 120f)

                            if (isDoubleTapCandidate) {
                                singleTapJob?.cancel()
                                singleTapJob = null
                            }

                            val longPressJob = scope.launch {
                                delay(450L)
                                if (!isDrag && !isMultiTouch) {
                                    isLongPressActive = true
                                    speedBeforeLongPress = playbackSpeed
                                    viewModel.setSpeed(2.0f)
                                    triggerOsd(
                                        OsdState(
                                            icon = Icons.Default.Speed,
                                            title = "2.0x Fast Forward",
                                            valueText = "Speed Boost"
                                        ),
                                        durationMs = 3000L
                                    )
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val activePointers = event.changes.filter { it.pressed }

                                if (activePointers.isEmpty()) {
                                    break
                                }

                                if (activePointers.size >= 2) {
                                    isMultiTouch = true
                                    longPressJob.cancel()
                                    if (isLongPressActive) {
                                        isLongPressActive = false
                                        viewModel.setSpeed(speedBeforeLongPress)
                                    }

                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5.0f)
                                    panOffsetX += pan.x
                                    panOffsetY += pan.y
                                    event.changes.forEach { it.consume() }
                                    continue
                                }

                                val pointer = activePointers[0]
                                val currentPos = pointer.position
                                val dx = currentPos.x - startPos.x
                                val dy = currentPos.y - startPos.y
                                val deltaY = pointer.positionChange().y

                                if (!isDrag && !isMultiTouch) {
                                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                    if (dist > touchSlop) {
                                        isDrag = true
                                        longPressJob.cancel()
                                        if (isLongPressActive) {
                                            isLongPressActive = false
                                            viewModel.setSpeed(speedBeforeLongPress)
                                        }

                                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                            isHorizontalScrub = true
                                            activeGesture = ActiveGesture.SEEK
                                        } else {
                                            if (startPos.x < screenWidth / 2f) {
                                                isVerticalBrightness = true
                                                activeGesture = ActiveGesture.BRIGHTNESS
                                            } else {
                                                isVerticalVolume = true
                                                activeGesture = ActiveGesture.VOLUME
                                            }
                                        }
                                    }
                                }

                                if (isDrag) {
                                    pointer.consume()

                                    when {
                                        isHorizontalScrub -> {
                                            val seekFraction = (currentPos.x - startPos.x) / screenWidth
                                            val deltaMs = (seekFraction * 90_000L).toLong()
                                            scrubTargetMs = (initialPositionMs + deltaMs).coerceIn(0L, durationMs)
                                            seekPreviewMs = scrubTargetMs
                                            seekDeltaMs = deltaMs
                                        }
                                        isVerticalBrightness -> {
                                            val deltaFraction = -deltaY / screenHeight
                                            localBrightness = (localBrightness + deltaFraction * 1.5f).coerceIn(0.01f, 1.0f)
                                            setWindowBrightness(activity, localBrightness)
                                        }
                                        isVerticalVolume -> {
                                            val deltaFraction = -deltaY / screenHeight
                                            val currentVolPct = volumeBoostPercent
                                            val newVolPct = (currentVolPct + deltaFraction * 150f).coerceIn(0f, 200f)
                                            if (newVolPct > currentVolPct) {
                                                viewModel.increaseVolume()
                                            } else if (newVolPct < currentVolPct) {
                                                viewModel.decreaseVolume()
                                            }
                                        }
                                    }
                                }
                            }

                            longPressJob.cancel()

                            if (isLongPressActive) {
                                isLongPressActive = false
                                viewModel.setSpeed(speedBeforeLongPress)
                                triggerOsd(
                                    OsdState(
                                        icon = Icons.Default.Speed,
                                        title = "${String.format(Locale.US, "%.1f", speedBeforeLongPress)}x",
                                        valueText = "Speed Restored"
                                    )
                                )
                            } else if (isDrag) {
                                if (isHorizontalScrub) {
                                    exoPlayer.seekTo(scrubTargetMs)
                                }
                                activeGesture = ActiveGesture.NONE
                                isHorizontalScrub = false
                                isVerticalBrightness = false
                                isVerticalVolume = false
                            } else if (!isMultiTouch) {
                                if (isDoubleTapCandidate) {
                                    lastTapTime = 0L
                                    val seekSecs = appSettings.doubleTapSeekSecs
                                    val leftThird = screenWidth / 3f
                                    val rightThird = (screenWidth * 2f) / 3f

                                    when {
                                        startPos.x < leftThird -> {
                                            val newPos = (exoPlayer.currentPosition - seekSecs * 1000L).coerceAtLeast(0L)
                                            exoPlayer.seekTo(newPos)
                                            doubleTapSeekState = Pair(false, seekSecs)
                                            triggerOsd(
                                                OsdState(
                                                    icon = Icons.Default.FastRewind,
                                                    title = "-${seekSecs}s",
                                                    valueText = formatTime(newPos),
                                                    alignment = Alignment.CenterStart
                                                )
                                            )
                                        }
                                        startPos.x > rightThird -> {
                                            val newPos = (exoPlayer.currentPosition + seekSecs * 1000L).coerceAtMost(duration)
                                            exoPlayer.seekTo(newPos)
                                            doubleTapSeekState = Pair(true, seekSecs)
                                            triggerOsd(
                                                OsdState(
                                                    icon = Icons.Default.FastForward,
                                                    title = "+${seekSecs}s",
                                                    valueText = formatTime(newPos),
                                                    alignment = Alignment.CenterEnd
                                                )
                                            )
                                        }
                                        else -> {
                                            viewModel.togglePlayPause()
                                            triggerOsd(
                                                OsdState(
                                                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    title = if (isPlaying) "Paused" else "Playing"
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    lastTapTime = downTime
                                    lastTapPos = startPos
                                    singleTapJob = scope.launch {
                                        delay(250L)
                                        viewModel.toggleControls()
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // â”€â”€ 4. DOUBLE-TAP SEEK RIPPLE OSD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        doubleTapSeekState?.let { (isForward, seekAmount) ->
            LaunchedEffect(doubleTapSeekState) {
                delay(650)
                doubleTapSeekState = null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = if (isForward) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.size(90.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isForward) "+${seekAmount}s" else "-${seekAmount}s",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // â”€â”€ 5. OSD NOTIFICATION OVERLAY â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        AnimatedVisibility(
            visible = osdState != null,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.85f),
            modifier = Modifier
                .align(osdState?.alignment ?: Alignment.Center)
                .padding(32.dp)
        ) {
            osdState?.let { osd ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xDD1E1E1E),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 18.dp)
                            .width(if (osd.progress != null) 160.dp else 130.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = osd.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = osd.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        if (osd.valueText.isNotBlank()) {
                            Text(
                                text = osd.valueText,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (osd.progress != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { osd.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.2f),
                            )
                        }
                    }
                }
            }
        }

        // ── 5.5 MINIMALIST GESTURE HUD (AUTO-HIDING) ────────────────────────
        AnimatedVisibility(
            visible = activeGesture != ActiveGesture.NONE && !screenLocked && !isInPip,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.9f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (activeGesture) {
                    ActiveGesture.BRIGHTNESS -> {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xDD121216),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 36.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 20.dp)
                                    .width(44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrightnessMedium,
                                    contentDescription = "Brightness",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(24.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .fillMaxHeight(fraction = localBrightness.coerceIn(0.01f, 1f))
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFFFD54F))
                                    )
                                }
                                Text(
                                    text = "${(localBrightness * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    ActiveGesture.VOLUME -> {
                        val isBoost = volumeBoostPercent > 100f
                        val boostColor = Color(0xFFFFB300)
                        val activeColor = if (isBoost) boostColor else MaterialTheme.colorScheme.primary
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xDD121216),
                            border = BorderStroke(1.dp, if (isBoost) boostColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f)),
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 36.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 20.dp)
                                    .width(44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        volumeBoostPercent <= 0f -> Icons.Default.VolumeOff
                                        volumeBoostPercent > 100f -> Icons.Default.VolumeUp
                                        else -> Icons.Default.VolumeDown
                                    },
                                    contentDescription = "Volume",
                                    tint = activeColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .fillMaxHeight(fraction = (volumeBoostPercent / 200f).coerceIn(0f, 1f))
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(activeColor)
                                    )
                                }
                                Text(
                                    text = "${volumeBoostPercent.toInt()}%",
                                    color = if (isBoost) boostColor else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    ActiveGesture.SEEK -> {
                        val isForward = seekDeltaMs >= 0L
                        val deltaSec = (seekDeltaMs / 1000L).toInt()
                        val deltaFormatted = if (deltaSec >= 0) "+${deltaSec}s" else "${deltaSec}s"
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xEE121216),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            shadowElevation = 12.dp,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                                    .width(220.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = deltaFormatted,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "${formatTime(seekPreviewMs)} / ${formatTime(duration)}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                                LinearProgressIndicator(
                                    progress = { (seekPreviewMs.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                )
                            }
                        }
                    }
                    ActiveGesture.NONE -> {}
                }
            }
        }

        // ── 6. CONTROLS OVERLAY ──────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls && !screenLocked && !isInPip,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                viewModel.hideControls()
                            }
                        )
                    }
            ) {
                // ── GLASSMORPHIC TOP APP BAR ─────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.handleBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = videoFileName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .basicMarquee()
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = resolutionBadge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (showCodecHud && codecInfo.isNotBlank()) {
                            Text(
                                text = codecInfo,
                                color = Color(0xFF81D4FA),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Screen Lock button
                    IconButton(
                        onClick = {
                            viewModel.toggleScreenLock()
                            triggerOsd(OsdState(icon = Icons.Default.Lock, title = "Screen Locked", valueText = "Tap unlock to resume"))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Lock Screen",
                            tint = Color.White
                        )
                    }

                    // Screen orientation toggle button
                    IconButton(
                        onClick = {
                            toggleScreenOrientation()
                            triggerOsd(
                                OsdState(
                                    icon = if (isLandscape) Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                                    title = "Orientation",
                                    valueText = if (isLandscape) "Portrait" else "Landscape"
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                            contentDescription = "Toggle Screen Orientation",
                            tint = Color.White
                        )
                    }

                    // Player Settings / More
                    IconButton(
                        onClick = { showPlayerSettingsSheet = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Player Settings",
                            tint = Color.White
                        )
                    }
                }

                // ── CENTER TRANSPORT CONTROLS ────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track
                    IconButton(
                        onClick = {
                            viewModel.playPrevious()
                            viewModel.scheduleControlsHide()
                        },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Seek -10s
                    IconButton(
                        onClick = {
                            val newPos = (currentPosition - appSettings.doubleTapSeekSecs * 1000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            viewModel.scheduleControlsHide()
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // Play / Pause FAB
                    FloatingActionButton(
                        onClick = {
                            viewModel.togglePlayPause()
                            viewModel.scheduleControlsHide()
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(72.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // Seek +10s
                    IconButton(
                        onClick = {
                            val newPos = (currentPosition + appSettings.doubleTapSeekSecs * 1000L).coerceAtMost(duration)
                            exoPlayer.seekTo(newPos)
                            viewModel.scheduleControlsHide()
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Fast Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // Next Track
                    IconButton(
                        onClick = {
                            viewModel.playNext()
                            viewModel.scheduleControlsHide()
                        },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // ── A-B REPEAT ROW ───────────────────────────────────────────────
                if (abRepeatAMs != -1L) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 140.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "[A] ${formatTime(abRepeatAMs)}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(text = "→", color = Color.White, fontSize = 13.sp)
                            Text(
                                text = if (abRepeatBMs != -1L) "[B] ${formatTime(abRepeatBMs)}" else "[B] Not set",
                                color = if (abRepeatBMs != -1L) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (abRepeatBMs == -1L) {
                                TextButton(
                                    onClick = { viewModel.setAbRepeatB() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Set B", fontSize = 12.sp)
                                }
                            }
                            IconButton(
                                onClick = { viewModel.clearAbRepeat() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear A-B Loop",
                                    tint = Color.Red.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ── MODERN FLOATING BOTTOM CONTROL DECK ───────────────────────────
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xDD121216),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // Time labels & Live Scrub Display
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )

                            Text(
                                text = formatTime(duration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Custom Video Seekbar with Chapters and Bookmarks
                        VideoSeekBar(
                            currentPosition = currentPosition,
                            duration = duration,
                            bufferedPosition = bufferedPosition,
                            chapters = currentChapters,
                            bookmarks = currentBookmarks,
                            onSeek = { targetMs ->
                                viewModel.seekTo(targetMs)
                                viewModel.scheduleControlsHide()
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Minimalist Essential Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Aspect ratio toggle (Fit / Fill / Zoom / 16:9 / Center Crop)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable {
                                    viewModel.cycleAspectRatio()
                                    viewModel.scheduleControlsHide()
                                    triggerOsd(
                                        OsdState(
                                            icon = Icons.Default.AspectRatio,
                                            title = "Aspect Ratio",
                                            valueText = aspectRatioMode
                                        )
                                    )
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "Aspect Ratio: $aspectRatioMode",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = aspectRatioMode,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Subtitles & Audio Selector
                                IconButton(
                                    onClick = {
                                        subtitleAudioSheetTab = 0
                                        showSubtitleAudioSheet = true
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = "Subtitles & Audio",
                                        tint = if (primarySubtitleIndex != -1) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Picture-in-Picture
                                IconButton(
                                    onClick = { viewModel.enterPip() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPicture,
                                        contentDescription = "Picture in Picture",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 7. SCREEN LOCK OVERLAY ──────────────────────────────────────────
        if (screenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Swipe up to unlock
                            if (dragAmount.y < -30f) {
                                viewModel.toggleScreenLock()
                                triggerOsd(OsdState(icon = Icons.Default.LockOpen, title = "Screen Unlocked"))
                            }
                        }
                    }
            ) {
                // Glowing semi-transparent unlock floating button in the corner
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 24.dp)
                        .size(54.dp)
                        .clickable {
                            viewModel.toggleScreenLock()
                            triggerOsd(OsdState(icon = Icons.Default.LockOpen, title = "Screen Unlocked"))
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Tap to unlock",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // â”€â”€ 8. GIF RECORDER OVERLAY â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        when (val state = gifMakerState) {
            is GifMakerState.Recording -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recording: ${state.progressMs / 1000}s / 5s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            is GifMakerState.Done -> {
                Toast.makeText(context, "Clip saved: ${state.path}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // BOTTOM SHEETS & DIALOGS
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    if (showSubtitleAudioSheet) {
        SubtitleAudioSheet(
            controller = subtitleController,
            viewModel = viewModel,
            player = exoPlayer,
            initialTab = subtitleAudioSheetTab,
            onDismiss = { showSubtitleAudioSheet = false }
        )
    }

    if (showSpeedDialog) {
        SpeedPickerDialog(
            viewModel = viewModel,
            currentSpeed = playbackSpeed,
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showEqualizerSheet) {
        EqualizerSheet(
            viewModel = viewModel,
            onDismiss = { showEqualizerSheet = false }
        )
    }

    if (showFiltersDialog) {
        VideoFiltersDialog(
            currentFilters = colorFilters,
            onApply = { colorFilters = it },
            onDismiss = { showFiltersDialog = false }
        )
    }

    if (showBookmarkDialog) {
        AddBookmarkDialog(
            currentPosition = currentPosition,
            bookmarks = currentBookmarks,
            onAdd = { label -> viewModel.addBookmark(label) },
            onDelete = { id -> viewModel.deleteBookmark(id) },
            onSeek = { pos -> exoPlayer.seekTo(pos) },
            onDismiss = { showBookmarkDialog = false }
        )
    }

    if (showId3EditorSheet) {
        Id3TagEditorBottomSheet(
            mediaPath = mediaPath,
            viewModel = viewModel,
            onDismiss = { showId3EditorSheet = false }
        )
    }

    if (showPlayerSettingsSheet) {
        PlayerSettingsSheet(
            viewModel = viewModel,
            subtitleController = subtitleController,
            playbackSpeed = playbackSpeed,
            abRepeatAMs = abRepeatAMs,
            abRepeatBMs = abRepeatBMs,
            currentPosition = currentPosition,
            colorFilters = colorFilters,
            showCodecHud = showCodecHud,
            onColorFiltersChange = { colorFilters = it },
            onToggleCodecHud = { viewModel.toggleCodecHud() },
            onOpenEqualizer = {
                showPlayerSettingsSheet = false
                showEqualizerSheet = true
            },
            onOpenId3Editor = {
                showPlayerSettingsSheet = false
                showId3EditorSheet = true
            },
            onDismiss = { showPlayerSettingsSheet = false }
        )
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// CUSTOM CANVAS SEEKBAR COMPOSABLE
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun VideoSeekBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    chapters: List<Chapter>,
    bookmarks: List<Bookmark>,
    onSeek: (Long) -> Unit
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val safeDuration = duration.coerceAtLeast(1L)
    val actualProgress = (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayProgress = if (isDragging) dragProgress else actualProgress
    val bufferProgress = (bufferedPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(safeDuration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val progress = (offset.x / size.width).coerceIn(0f, 1f)
                        dragProgress = progress
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val progress = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragProgress = progress
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek((dragProgress * safeDuration).toLong())
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            }
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val progress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((progress * safeDuration).toLong())
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerY = heightPx / 2f
        val trackHeightPx = with(density) { 4.dp.toPx() }
        val thumbRadiusPx = with(density) { (if (isDragging) 9.dp else 6.dp).toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Inactive Track Background
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = Size(widthPx, trackHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f)
            )

            // 2. Buffer Progress Track
            if (bufferProgress > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(widthPx * bufferProgress, trackHeightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f)
                )
            }

            // 3. Active Played Progress Track
            if (displayProgress > 0f) {
                drawRoundRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(widthPx * displayProgress, trackHeightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f)
                )
            }

            // 4. Chapter Markers (Thin vertical white lines)
            chapters.forEach { chapter ->
                val chapterX = (chapter.titleMs.toFloat() / safeDuration.toFloat()) * widthPx
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(chapterX, centerY - trackHeightPx * 1.5f),
                    end = Offset(chapterX, centerY + trackHeightPx * 1.5f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. Bookmark Markers (Diamond shapes in amber gold)
            bookmarks.forEach { bookmark ->
                val bookmarkX = (bookmark.positionMs.toFloat() / safeDuration.toFloat()) * widthPx
                val diamondRadius = 5.dp.toPx()
                val diamondPath = Path().apply {
                    moveTo(bookmarkX, centerY - diamondRadius)
                    lineTo(bookmarkX + diamondRadius, centerY)
                    lineTo(bookmarkX, centerY + diamondRadius)
                    lineTo(bookmarkX - diamondRadius, centerY)
                    close()
                }
                drawPath(path = diamondPath, color = Color(0xFFFFD54F))
            }

            // 6. Thumb
            val thumbX = widthPx * displayProgress
            drawCircle(
                color = Color.White,
                radius = thumbRadiusPx,
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = thumbRadiusPx * 0.65f,
                center = Offset(thumbX, centerY)
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// COMPOSABLE: SubtitleAudioSheet
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleAudioSheet(
    controller: SubtitleAndAudioController,
    viewModel: SmartVideoPlayerViewModel,
    player: Player,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    val subtitles by controller.availableSubtitles.collectAsStateWithLifecycle()
    val audioTracks by controller.availableAudioTracks.collectAsStateWithLifecycle()
    val primarySubtitleIndex by controller.primarySubtitleIndex.collectAsStateWithLifecycle()
    val secondarySubtitleIndex by controller.secondarySubtitleIndex.collectAsStateWithLifecycle()
    val activeAudioIndex by controller.activeAudioTrackIndex.collectAsStateWithLifecycle()
    val subtitleStyle by controller.subtitleStyle.collectAsStateWithLifecycle()
    val subtitleSync by controller.subtitleSync.collectAsStateWithLifecycle()
    val audioDelayMs by controller.audioDelayMs.collectAsStateWithLifecycle()
    val dualSubtitleEnabled by controller.dualSubtitleEnabled.collectAsStateWithLifecycle()
    val subtitleEncoding by controller.subtitleEncoding.collectAsStateWithLifecycle()
    val channelMode by controller.channelMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // SAF Picker for external subtitle file
    val subtitleFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val file = File(it.path ?: "")
                    if (file.exists()) {
                        controller.loadSubtitleFile(file)
                        controller.loadTracksFromPlayer(player)
                        Toast.makeText(context, "Subtitle loaded", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load subtitle file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // SAF Picker for external audio file
    val audioFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            controller.setExternalAudioPath(it.path)
            Toast.makeText(context, "External audio track set", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Subtitles (${subtitles.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audio (${audioTracks.size})") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // â”€â”€ SUBTITLES TAB â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "Track Selection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // "None" option
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { controller.selectPrimarySubtitle(-1, player) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Off (Disable Subtitles)",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (primarySubtitleIndex == -1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (primarySubtitleIndex == -1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (primarySubtitleIndex == -1) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // Available subtitles
                        items(subtitles) { track ->
                            val isSelected = track.index == primarySubtitleIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                                    )
                                    .clickable { controller.selectPrimarySubtitle(track.index, player) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Lang: ${track.language} Â· Source: ${track.source}${if (track.isForced) " Â· Forced" else ""}${if (track.isSDH) " Â· SDH" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // Add External Subtitle Button
                        item {
                            OutlinedButton(
                                onClick = { subtitleFilePicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add External Subtitle (.srt, .vtt, .ass)")
                            }
                        }

                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                        // Dual Subtitles Toggle
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Dual Subtitles", fontWeight = FontWeight.SemiBold)
                                    Text("Display secondary subtitle at top of screen", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = dualSubtitleEnabled,
                                    onCheckedChange = { controller.setDualSubtitleEnabled(it) }
                                )
                            }
                        }

                        if (dualSubtitleEnabled) {
                            item {
                                Text("Secondary Subtitle Track", style = MaterialTheme.typography.labelLarge)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = secondarySubtitleIndex == -1,
                                            onClick = { controller.selectSecondarySubtitle(-1) },
                                            label = { Text("None") }
                                        )
                                    }
                                    items(subtitles) { track ->
                                        FilterChip(
                                            selected = secondarySubtitleIndex == track.index,
                                            onClick = { controller.selectSecondarySubtitle(track.index) },
                                            label = { Text(track.language.uppercase()) }
                                        )
                                    }
                                }
                            }
                        }

                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                        // Subtitle Delay / Sync Row
                        item {
                            Text("Subtitle Delay (Sync)", fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(onClick = { controller.adjustSubtitleDelay(-500L) }) { Text("-500ms") }
                                OutlinedButton(onClick = { controller.adjustSubtitleDelay(-100L) }) { Text("-100ms") }
                                Text(
                                    text = "${subtitleSync.delayMs}ms",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedButton(onClick = { controller.adjustSubtitleDelay(+100L) }) { Text("+100ms") }
                                OutlinedButton(onClick = { controller.adjustSubtitleDelay(+500L) }) { Text("+500ms") }
                            }
                        }

                        // Subtitle Speed Multiplier
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtitle Speed", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", subtitleSync.speedMultiplier)}x",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = subtitleSync.speedMultiplier,
                                onValueChange = { controller.setSubtitleSpeedMultiplier(it) },
                                valueRange = 0.5f..2.0f
                            )
                        }

                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                        // Subtitle Style Subsection
                        item {
                            Text("Subtitle Appearance & Styling", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        // Font size
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Font Size")
                                Text("${subtitleStyle.fontSizeSp} sp", fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = subtitleStyle.fontSizeSp.toFloat(),
                                onValueChange = { size ->
                                    controller.updateSubtitleStyle { it.copy(fontSizeSp = size.roundToInt()) }
                                },
                                valueRange = 10f..42f
                            )
                        }

                        // Font Family
                        item {
                            Text("Font Family", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                val fonts = listOf("SANS", "SERIF", "MONO", "BOLD")
                                items(fonts) { font ->
                                    FilterChip(
                                        selected = subtitleStyle.fontFamily.equals(font, ignoreCase = true),
                                        onClick = { controller.updateSubtitleStyle { it.copy(fontFamily = font) } },
                                        label = { Text(font) }
                                    )
                                }
                            }
                        }

                        // Text Color Presets
                        item {
                            Text("Text Color", style = MaterialTheme.typography.labelLarge)
                            val colors = listOf(
                                "White" to 0xFFFFFFFF.toInt(),
                                "Yellow" to 0xFFFFEB3B.toInt(),
                                "Cyan" to 0xFF00E5FF.toInt(),
                                "Green" to 0xFF76FF03.toInt(),
                                "Magenta" to 0xFFFF4081.toInt()
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                items(colors) { (name, colorArgb) ->
                                    FilterChip(
                                        selected = subtitleStyle.textColorArgb == colorArgb,
                                        onClick = { controller.updateSubtitleStyle { it.copy(textColorArgb = colorArgb) } },
                                        label = { Text(name) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(colorArgb))
                                                    .border(1.dp, Color.Gray, CircleShape)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Outline & Drop Shadow
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Drop Shadow", fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = subtitleStyle.dropShadow,
                                    onCheckedChange = { checked ->
                                        controller.updateSubtitleStyle { it.copy(dropShadow = checked) }
                                    }
                                )
                            }
                        }

                        // Background Opacity
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Background Box Opacity")
                                Text("${(subtitleStyle.bgOpacity * 100f).roundToInt()}%", fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = subtitleStyle.bgOpacity,
                                onValueChange = { op ->
                                    controller.updateSubtitleStyle { it.copy(bgOpacity = op) }
                                },
                                valueRange = 0f..1f
                            )
                        }

                        // Bottom Margin
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bottom Margin")
                                Text("${subtitleStyle.bottomMarginDp} dp", fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = subtitleStyle.bottomMarginDp.toFloat(),
                                onValueChange = { bm ->
                                    controller.updateSubtitleStyle { it.copy(bottomMarginDp = bm.roundToInt()) }
                                },
                                valueRange = 0f..80f
                            )
                        }

                        // Encoding selector
                        item {
                            Text("Text Encoding", style = MaterialTheme.typography.labelLarge)
                            val encodings = listOf("UTF-8", "UTF-16", "Windows-1252", "ISO-8859-1", "Shift-JIS", "Big5", "GB18030")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                items(encodings) { enc ->
                                    FilterChip(
                                        selected = subtitleEncoding.equals(enc, ignoreCase = true),
                                        onClick = { controller.setSubtitleEncoding(enc) },
                                        label = { Text(enc) }
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // â”€â”€ AUDIO TAB â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "Audio Tracks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(audioTracks) { track ->
                            val isSelected = track.index == activeAudioIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                                    )
                                    .clickable { controller.selectAudioTrack(track.index, player) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${track.codec} Â· ${track.channels} Â· ${track.sampleRate / 1000}kHz Â· ${track.bitrate / 1000}kbps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // Load external audio
                        item {
                            OutlinedButton(
                                onClick = { audioFilePicker.launch(arrayOf("audio/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Load External Audio Track")
                            }
                        }

                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                        // Audio Delay / Sync
                        item {
                            Text("Audio Delay (Lip Sync)", fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(onClick = { controller.adjustAudioDelay(-500L) }) { Text("-500ms") }
                                OutlinedButton(onClick = { controller.adjustAudioDelay(-100L) }) { Text("-100ms") }
                                Text(
                                    text = "${audioDelayMs}ms",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedButton(onClick = { controller.adjustAudioDelay(+100L) }) { Text("+100ms") }
                                OutlinedButton(onClick = { controller.adjustAudioDelay(+500L) }) { Text("+500ms") }
                            }
                        }

                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                        // Channel Mode Selector
                        item {
                            Text("Channel Output Mode", fontWeight = FontWeight.SemiBold)
                            val modes = listOf("STEREO", "LEFT", "RIGHT", "MONO", "SURROUND", "NIGHT_MODE")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                items(modes) { mode ->
                                    FilterChip(
                                        selected = channelMode.equals(mode, ignoreCase = true),
                                        onClick = { controller.setChannelMode(mode) },
                                        label = { Text(mode.replace("_", " ")) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// COMPOSABLE: SpeedPickerDialog
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun SpeedPickerDialog(
    viewModel: SmartVideoPlayerViewModel,
    currentSpeed: Float,
    onDismiss: () -> Unit
) {
    var speedVal by remember { mutableFloatStateOf(currentSpeed) }
    val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preset chips
                Text("Presets", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        FilterChip(
                            selected = abs(speedVal - preset) < 0.01f,
                            onClick = {
                                speedVal = preset
                                viewModel.setSpeed(preset)
                            },
                            label = { Text("${preset}x") }
                        )
                    }
                }

                // Custom slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Custom Speed", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${String.format(Locale.US, "%.2f", speedVal)}x",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Slider(
                    value = speedVal,
                    onValueChange = {
                        speedVal = it
                        viewModel.setSpeed(it)
                    },
                    valueRange = 0.1f..8.0f
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                speedVal = 1.0f
                viewModel.setSpeed(1.0f)
            }) {
                Text("Reset (1.0x)")
            }
        }
    )
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// COMPOSABLE: EqualizerSheet
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    viewModel: SmartVideoPlayerViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val presets = listOf(
        "Flat", "Bass Boost", "Vocal", "Cinema", "Rock", "Pop",
        "Headphones", "Custom", "Jazz", "Classical", "Dance", "Acoustic"
    )
    val bandFrequencies = listOf(
        "32Hz", "60Hz", "150Hz", "250Hz", "400Hz",
        "1kHz", "2.4kHz", "6kHz", "12kHz", "15kHz"
    )

    var currentPreset by remember { mutableStateOf(settings.eqPreset) }
    var bandGains by remember {
        mutableStateOf(
            try {
                val arr = org.json.JSONArray(settings.eqBandGainsJson)
                IntArray(10) { arr.optInt(it, 0) }
            } catch (e: Exception) {
                IntArray(10) { 0 }
            }
        )
    }
    var preampMb by remember { mutableIntStateOf(settings.eqPreampMb) }
    var reverbPreset by remember { mutableIntStateOf(settings.eqReverbPreset) }
    var sleepTimerMinutes by remember { mutableIntStateOf(settings.sleepTimerMinutes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxHeight(0.85f)
        ) {
            Text("Audio Equalizer & FX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preset selector chips
                item {
                    Text("Presets", style = MaterialTheme.typography.labelLarge)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(presets) { preset ->
                            FilterChip(
                                selected = currentPreset.equals(preset, ignoreCase = true),
                                onClick = {
                                    currentPreset = preset
                                    viewModel.setEqPreset(preset)
                                    // Set characteristic curve for presets
                                    val newGains = when (preset) {
                                        "Bass Boost" -> intArrayOf(600, 500, 400, 200, 100, 0, 0, 0, 0, 0)
                                        "Vocal" -> intArrayOf(-200, -100, 100, 300, 500, 600, 500, 200, 0, -100)
                                        "Cinema" -> intArrayOf(400, 300, 100, 0, -100, 100, 300, 400, 500, 500)
                                        "Rock" -> intArrayOf(500, 400, 200, -100, -200, 100, 300, 400, 500, 600)
                                        "Pop" -> intArrayOf(-100, 100, 300, 400, 400, 300, 100, -100, -100, -200)
                                        "Headphones" -> intArrayOf(300, 400, 200, 0, 100, 200, 300, 400, 400, 300)
                                        else -> IntArray(10) { 0 }
                                    }
                                    bandGains = newGains
                                    viewModel.setEqBandGains(newGains)
                                },
                                label = { Text(preset) }
                            )
                        }
                    }
                }

                // Preamp Slider
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pre-amp Gain", fontWeight = FontWeight.SemiBold)
                        Text("${String.format(Locale.US, "%+.1f", preampMb / 100f)} dB", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = preampMb.toFloat(),
                        onValueChange = {
                            preampMb = it.roundToInt()
                            viewModel.setEqPreamp(preampMb)
                        },
                        valueRange = -1500f..1500f
                    )
                }

                item { HorizontalDivider() }

                // 10-Band Sliders
                item {
                    Text("10-Band Equalizer (-15 dB to +15 dB)", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                itemsIndexed(bandFrequencies) { idx, freq ->
                    val gain = bandGains.getOrElse(idx) { 0 }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = freq, modifier = Modifier.width(60.dp), fontSize = 13.sp)
                        Slider(
                            value = gain.toFloat(),
                            onValueChange = { newVal ->
                                val copy = bandGains.clone()
                                copy[idx] = newVal.roundToInt()
                                bandGains = copy
                                currentPreset = "Custom"
                                viewModel.setEqBandGains(copy)
                            },
                            valueRange = -1500f..1500f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${String.format(Locale.US, "%+.1f", gain / 100f)} dB",
                            modifier = Modifier.width(65.dp),
                            textAlign = TextAlign.End,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                item { HorizontalDivider() }

                // Reverb Selector
                item {
                    Text("Reverb Environment", fontWeight = FontWeight.SemiBold)
                    val reverbs = listOf("None" to -1, "Small Room" to 0, "Large Room" to 1, "Auditorium" to 2, "Cave" to 3, "Plate" to 4)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(reverbs) { (name, id) ->
                            FilterChip(
                                selected = reverbPreset == id,
                                onClick = {
                                    reverbPreset = id
                                    viewModel.setEqReverb(id)
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }

                item { HorizontalDivider() }

                // Sleep Timer
                item {
                    Text("Sleep Timer", fontWeight = FontWeight.SemiBold)
                    val timers = listOf("Off" to -1, "15m" to 15, "30m" to 30, "45m" to 45, "60m" to 60, "90m" to 90)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(timers) { (name, mins) ->
                            FilterChip(
                                selected = sleepTimerMinutes == mins,
                                onClick = {
                                    sleepTimerMinutes = mins
                                    viewModel.setSleepTimerMinutes(mins)
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply & Close")
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// COMPOSABLE: VideoFiltersDialog
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun VideoFiltersDialog(
    currentFilters: VideoColorFilters,
    onApply: (VideoColorFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var brightness by remember { mutableFloatStateOf(currentFilters.brightness) }
    var contrast by remember { mutableFloatStateOf(currentFilters.contrast) }
    var saturation by remember { mutableFloatStateOf(currentFilters.saturation) }
    var mirrorHorizontal by remember { mutableStateOf(currentFilters.mirrorHorizontal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Color Filters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Brightness
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Brightness")
                    Text("${(brightness * 100f).roundToInt()}%", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        onApply(VideoColorFilters(brightness, contrast, saturation, mirrorHorizontal))
                    },
                    valueRange = -1.0f..1.0f
                )

                // Contrast
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Contrast")
                    Text("${(contrast * 100f).roundToInt()}%", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = contrast,
                    onValueChange = {
                        contrast = it
                        onApply(VideoColorFilters(brightness, contrast, saturation, mirrorHorizontal))
                    },
                    valueRange = 0.5f..2.0f
                )

                // Saturation
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saturation")
                    Text("${(saturation * 100f).roundToInt()}%", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        onApply(VideoColorFilters(brightness, contrast, saturation, mirrorHorizontal))
                    },
                    valueRange = 0.0f..2.0f
                )

                // Mirror / Flip Horizontal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mirror / Flip Horizontal")
                    Switch(
                        checked = mirrorHorizontal,
                        onCheckedChange = {
                            mirrorHorizontal = it
                            onApply(VideoColorFilters(brightness, contrast, saturation, mirrorHorizontal))
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    brightness = 0f
                    contrast = 1f
                    saturation = 1f
                    mirrorHorizontal = false
                    onApply(VideoColorFilters(0f, 1f, 1f, false))
                }
            ) {
                Text("Reset")
            }
        }
    )
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// COMPOSABLE: AddBookmarkDialog
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun AddBookmarkDialog(
    currentPosition: Long,
    bookmarks: List<Bookmark>,
    onAdd: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var labelText by remember { mutableStateOf("Bookmark at ${formatTime(currentPosition)}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmarks") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (labelText.isNotBlank()) {
                            onAdd(labelText)
                            labelText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add at ${formatTime(currentPosition)}")
                }

                if (bookmarks.isNotEmpty()) {
                    Text("Saved Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(bookmarks) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { onSeek(bookmark.positionMs) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bookmark.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        formatTime(bookmark.positionMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(bookmark.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Id3TagEditorBottomSheet(
    mediaPath: String,
    viewModel: SmartVideoPlayerViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var albumArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedArtworkUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedArtworkUri = it
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    albumArtBitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(mediaPath) {
        try {
            val meta = viewModel.readAudioId3(mediaPath)
            title = meta.title
            artist = meta.artist
            album = meta.album
            year = meta.year
            genre = meta.genre
            albumArtBitmap = meta.albumArt
        } catch (e: Exception) {
            android.util.Log.w("Id3Editor", "Failed reading tags: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "ID3 Audio Tag & Artwork Editor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (albumArtBitmap != null) {
                            Image(
                                bitmap = albumArtBitmap!!.asImageBitmap(),
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.Audiotrack,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Album Artwork", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (albumArtBitmap != null) "Tap to replace cover art" else "Tap to choose cover art",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Select Image", fontSize = 12.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Track Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist / Performer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                try {
                                    var tempArtFile: File? = null
                                    selectedArtworkUri?.let { uri ->
                                        val temp = File(context.cacheDir, "art_${System.currentTimeMillis()}.jpg")
                                        context.contentResolver.openInputStream(uri)?.use { inStream ->
                                            FileOutputStream(temp).use { outStream ->
                                                inStream.copyTo(outStream)
                                            }
                                        }
                                        tempArtFile = temp
                                    }
                                    viewModel.updateAudioId3(
                                        path = mediaPath,
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        year = year,
                                        genre = genre,
                                        artworkFile = tempArtFile
                                    )
                                    tempArtFile?.delete()
                                    Toast.makeText(context, "Tags & artwork updated successfully", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to write tags: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Writing...")
                        } else {
                            Text("Save Tags")
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PLAYER SETTINGS MODAL BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    viewModel: SmartVideoPlayerViewModel,
    subtitleController: SubtitleAndAudioController,
    playbackSpeed: Float,
    abRepeatAMs: Long,
    abRepeatBMs: Long,
    currentPosition: Long,
    colorFilters: VideoColorFilters,
    showCodecHud: Boolean,
    onColorFiltersChange: (VideoColorFilters) -> Unit,
    onToggleCodecHud: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenId3Editor: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val subtitleSync by subtitleController.subtitleSync.collectAsStateWithLifecycle()
    val audioDelayMs by subtitleController.audioDelayMs.collectAsStateWithLifecycle()
    val volumeBoostPercent by viewModel.volumeBoostPercent.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF018181C),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Player Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Clear, contentDescription = "Close", tint = Color.White)
                }
            }

            // 1. Playback Speed Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playback Speed", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${String.format(Locale.US, "%.2f", playbackSpeed)}x",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                val speedOptions = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    speedOptions.forEach { spd ->
                        FilterChip(
                            selected = (spd == playbackSpeed),
                            onClick = { viewModel.setSpeed(spd) },
                            label = { Text("${spd}x", fontSize = 12.sp) }
                        )
                    }
                }
                Slider(
                    value = playbackSpeed,
                    onValueChange = { viewModel.setSpeed(it) },
                    valueRange = 0.25f..3.0f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            // 2. A-B Repeat Loop
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A-B Repeat Loop", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.setAbRepeatA() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (abRepeatAMs != -1L) "Point A: ${formatTime(abRepeatAMs)}" else "Set Point A", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.setAbRepeatB() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (abRepeatBMs != -1L) "Point B: ${formatTime(abRepeatBMs)}" else "Set Point B", fontSize = 12.sp)
                    }
                    if (abRepeatAMs != -1L || abRepeatBMs != -1L) {
                        IconButton(onClick = { viewModel.clearAbRepeat() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Loop", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            // 3. Audio & Subtitle Sync Controls
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Audio & Subtitle Sync", color = Color.White, fontWeight = FontWeight.SemiBold)
                // Subtitle Delay Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subtitle Delay", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { subtitleController.adjustSubtitleDelay(-50L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("-50ms", fontSize = 11.sp)
                        }
                        Text(
                            "${subtitleSync.delayMs}ms",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        OutlinedButton(
                            onClick = { subtitleController.adjustSubtitleDelay(50L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("+50ms", fontSize = 11.sp)
                        }
                    }
                }

                // Audio Delay Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio Delay", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { subtitleController.adjustAudioDelay(-50L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("-50ms", fontSize = 11.sp)
                        }
                        Text(
                            "${audioDelayMs}ms",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        OutlinedButton(
                            onClick = { subtitleController.adjustAudioDelay(50L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("+50ms", fontSize = 11.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            // 4. Equalizer & 200% Volume Boost
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Equalizer & Audio Boost", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onOpenEqualizer) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("10-Band EQ")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "200% Boost",
                            color = if (volumeBoostPercent > 100f) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = volumeBoostPercent > 100f,
                            onCheckedChange = { boosted ->
                                viewModel.setVolumeBoostPercent(if (boosted) 200f else 100f)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            // 5. Video Filters & Mirror
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Video Filters & Orientation", color = Color.White, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onColorFiltersChange(VideoColorFilters()) }) {
                        Text("Reset", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mirror Video (Horizontal Flip)", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Switch(
                        checked = colorFilters.mirrorHorizontal,
                        onCheckedChange = { onColorFiltersChange(colorFilters.copy(mirrorHorizontal = it)) }
                    )
                }

                Text("Brightness: ${String.format(Locale.US, "%.1f", colorFilters.brightness)}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Slider(
                    value = colorFilters.brightness,
                    onValueChange = { onColorFiltersChange(colorFilters.copy(brightness = it)) },
                    valueRange = -1.0f..1.0f
                )

                Text("Contrast: ${String.format(Locale.US, "%.1f", colorFilters.contrast)}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Slider(
                    value = colorFilters.contrast,
                    onValueChange = { onColorFiltersChange(colorFilters.copy(contrast = it)) },
                    valueRange = 0.5f..2.0f
                )

                Text("Saturation: ${String.format(Locale.US, "%.1f", colorFilters.saturation)}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Slider(
                    value = colorFilters.saturation,
                    onValueChange = { onColorFiltersChange(colorFilters.copy(saturation = it)) },
                    valueRange = 0.0f..2.0f
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            // 6. Tools & Utilities
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tools & Extras", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.startGifRecording()
                            Toast.makeText(context, "Recording 5s GIF...", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("5s GIF", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.captureScreenshot()
                            Toast.makeText(context, "Screenshot saved", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Screenshot", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Codec & Resolution HUD", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Switch(
                        checked = showCodecHud,
                        onCheckedChange = { onToggleCodecHud() }
                    )
                }

                // Sleep Timer
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sleep Timer", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, lbl) ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.setSleepTimerMinutes(mins)
                                    Toast.makeText(context, if (mins == 0) "Sleep timer cancelled" else "Sleep timer: $lbl", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(lbl, fontSize = 11.sp)
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenId3Editor()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Audio Tags & Metadata (ID3)")
                }
            }
        }
    }
}

