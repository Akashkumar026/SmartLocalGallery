package com.example.smartgallery.player

import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.Player
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global registry indicating whether active video/audio playback is currently ongoing.
 * Used by background AI and indexing workers to pause or throttle heavy compute,
 * and by MainActivity for keyboard event dispatch and PiP handling.
 */
object ActivePlayerRegistry {
    private val _isPlayerActive = AtomicBoolean(false)

    var isPlayerActive: Boolean
        get() = _isPlayerActive.get()
        set(value) { _isPlayerActive.set(value) }

    val isPlaying: Boolean
        get() = _isPlayerActive.get()

    @JvmName("updatePlayerActive")
    fun setPlayerActive(active: Boolean) {
        _isPlayerActive.set(active)
    }

    var activePlayer: Player? = null
    var activeViewModel: SmartVideoPlayerViewModel? = null
    var activeSubController: SubtitleAndAudioController? = null
    val pipModeFlow = mutableStateOf(false)
}
