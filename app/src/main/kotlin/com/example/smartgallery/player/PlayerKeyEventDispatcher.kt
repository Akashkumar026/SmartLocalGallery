package com.example.smartgallery.player

import android.view.KeyEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.smartgallery.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// PlayerKeyEventDispatcher
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Singleton
class PlayerKeyEventDispatcher @Inject constructor(
    private val settings: SettingsRepository
) {

    // Cached seek seconds (updated lazily on each dispatch from settings flow)
    private var _cachedSeekSecs: Int = 10
    private val seekSecs: Int
        get() {
            _cachedSeekSecs = runBlocking { settings.settingsFlow.first().doubleTapSeekSecs }
            return _cachedSeekSecs
        }

    /**
     * Dispatch a hardware key event to the appropriate player/controller action.
     * @return true if the event was consumed, false otherwise.
     */
    fun dispatch(
        keyCode: Int,
        event: KeyEvent,
        player: Player,
        controller: SubtitleAndAudioController,
        viewModel: SmartVideoPlayerViewModel
    ): Boolean {
        // Only handle key-down events
        if (event.action != KeyEvent.ACTION_DOWN) return false

        return when (keyCode) {

            // â”€â”€ Play / Pause â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                togglePlayPause(player)
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.play()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player.pause()
                true
            }

            KeyEvent.KEYCODE_MEDIA_STOP -> {
                player.pause()
                player.seekTo(0L)
                viewModel.savePlaybackPosition()
                true
            }

            // â”€â”€ Seek backward â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                val delta = if (event.isShiftPressed) -60_000L else -(seekSecs * 1000L)
                seekRelative(player, delta)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                val delta = if (event.isShiftPressed) +60_000L else +(seekSecs * 1000L)
                seekRelative(player, delta)
                true
            }

            // â”€â”€ YouTube-style J/L/K â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_J -> {
                seekRelative(player, -10_000L)
                true
            }

            KeyEvent.KEYCODE_L -> {
                viewModel.toggleScreenLock()
                true
            }

            KeyEvent.KEYCODE_K -> {
                togglePlayPause(player)
                true
            }

            // â”€â”€ Frame step â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_COMMA -> {
                stepFrame(player, -1)
                true
            }

            KeyEvent.KEYCODE_PERIOD -> {
                stepFrame(player, +1)
                true
            }

            // â”€â”€ Home / End â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_MOVE_HOME -> {
                player.seekTo(0L)
                true
            }

            KeyEvent.KEYCODE_MOVE_END -> {
                val duration = player.duration.takeIf { it > 0L } ?: return false
                player.seekTo((duration * 0.99).toLong())
                true
            }

            // â”€â”€ 0â€“9 percentage seek â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9 -> {
                val duration = player.duration.takeIf { it > 0L } ?: return false
                val fraction = (keyCode - KeyEvent.KEYCODE_0) * 0.1
                player.seekTo((duration * fraction).toLong())
                true
            }

            // â”€â”€ Next / Previous â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                viewModel.playNext()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_PAGE_UP -> {
                viewModel.playPrevious()
                true
            }

            // â”€â”€ Volume â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.increaseVolume()
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.decreaseVolume()
                true
            }

            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_M -> {
                viewModel.toggleMute()
                true
            }

            // â”€â”€ Subtitle track cycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_CAPTIONS -> {
                controller.cyclePrimarySubtitle()
                true
            }

            // â”€â”€ Audio track cycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> {
                controller.cycleAudioTrack(player)
                true
            }

            // â”€â”€ Playback speed â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_LEFT_BRACKET -> {
                viewModel.adjustSpeed(-0.1f)
                true
            }

            KeyEvent.KEYCODE_RIGHT_BRACKET -> {
                viewModel.adjustSpeed(+0.1f)
                true
            }

            KeyEvent.KEYCODE_BACKSLASH -> {
                viewModel.setSpeed(1.0f)
                true
            }

            // â”€â”€ Subtitle delay â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_G -> {
                controller.adjustSubtitleDelay(-50L)
                true
            }

            KeyEvent.KEYCODE_H -> {
                controller.adjustSubtitleDelay(+50L)
                true
            }

            // â”€â”€ Audio delay â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_F -> {
                if (event.isCtrlPressed) {
                    controller.adjustAudioDelay(-50L)
                } else {
                    viewModel.toggleScreenOrientation()
                }
                true
            }

            KeyEvent.KEYCODE_D -> {
                controller.adjustAudioDelay(+50L)
                true
            }

            KeyEvent.KEYCODE_A -> {
                if (event.isShiftPressed) {
                    viewModel.cycleAspectRatio()
                } else {
                    controller.cycleAudioTrack(player)
                }
                true
            }

            KeyEvent.KEYCODE_C -> {
                viewModel.cycleCropMode()
                true
            }

            KeyEvent.KEYCODE_R -> {
                viewModel.rotateVideo()
                true
            }

            KeyEvent.KEYCODE_S -> {
                if (event.isShiftPressed) {
                    viewModel.captureScreenshot()
                } else {
                    controller.cyclePrimarySubtitle()
                }
                true
            }

            KeyEvent.KEYCODE_O -> {
                viewModel.toggleScreenOrientation()
                true
            }

            // â”€â”€ Codec HUD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_I -> {
                viewModel.toggleCodecHud()
                true
            }

            // â”€â”€ Back / Escape â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BACK -> {
                viewModel.handleBack()
                true
            }

            // â”€â”€ Unhandled â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            else -> false
        }
    }

    // â”€â”€ Private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun togglePlayPause(player: Player) {
        if (player.isPlaying) player.pause() else player.play()
    }

    private fun seekRelative(player: Player, deltaMs: Long) {
        val duration = player.duration.takeIf { it > 0L } ?: return
        val current  = player.currentPosition
        val newPos   = (current + deltaMs).coerceIn(0L, duration)
        player.seekTo(newPos)
    }

    private fun stepFrame(player: Player, frames: Int) {
        // Attempt to infer frame rate from video format; default to 30 fps
        val frameRate = (player as? ExoPlayer)?.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
        val frameDurationMs = (1000.0 / frameRate).toLong().coerceAtLeast(1L)
        seekRelative(player, frames * frameDurationMs)
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Compose Modifier extension
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.playerKeyEvents(
    dispatcher: PlayerKeyEventDispatcher,
    player: Player,
    controller: SubtitleAndAudioController,
    viewModel: SmartVideoPlayerViewModel
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        dispatcher.dispatch(
            keyCode    = event.nativeKeyEvent.keyCode,
            event      = event.nativeKeyEvent,
            player     = player,
            controller = controller,
            viewModel  = viewModel
        )
    } else {
        false
    }
}

