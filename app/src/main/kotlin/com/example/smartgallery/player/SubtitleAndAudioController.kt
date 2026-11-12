package com.example.smartgallery.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import com.example.smartgallery.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Data classes
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

data class SubtitleTrack(
    val index: Int,
    val label: String,
    val language: String,
    val isForced: Boolean,
    val isSDH: Boolean,
    val source: String   // "embedded" | "external"
)

data class AudioTrack(
    val index: Int,
    val label: String,
    val language: String,
    val codec: String,
    val channels: String,
    val sampleRate: Int,
    val bitrate: Int
)

data class SubtitleStyle(
    val fontSizeSp: Int = 18,
    val fontFamily: String = "SANS",
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val outlineColorArgb: Int = 0xFF000000.toInt(),
    val outlineWidth: Float = 2f,
    val dropShadow: Boolean = true,
    val bgOpacity: Float = 0.6f,
    val bottomMarginDp: Int = 24
)

data class SubtitleSyncState(
    val delayMs: Long = 0L,
    val speedMultiplier: Float = 1.0f
)

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Controller
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Singleton
class SubtitleAndAudioController @Inject constructor(
    private val settings: SettingsRepository
) {

    // â”€â”€ State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private val _availableSubtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val availableSubtitles: StateFlow<List<SubtitleTrack>> = _availableSubtitles.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val availableAudioTracks: StateFlow<List<AudioTrack>> = _availableAudioTracks.asStateFlow()

    private val _primarySubtitleIndex = MutableStateFlow(-1)   // -1 = off
    val primarySubtitleIndex: StateFlow<Int> = _primarySubtitleIndex.asStateFlow()

    private val _secondarySubtitleIndex = MutableStateFlow(-1) // -1 = off
    val secondarySubtitleIndex: StateFlow<Int> = _secondarySubtitleIndex.asStateFlow()

    private val _activeAudioTrackIndex = MutableStateFlow(0)
    val activeAudioTrackIndex: StateFlow<Int> = _activeAudioTrackIndex.asStateFlow()

    private val _subtitleStyle = MutableStateFlow(SubtitleStyle())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    private val _subtitleSync = MutableStateFlow(SubtitleSyncState())
    val subtitleSync: StateFlow<SubtitleSyncState> = _subtitleSync.asStateFlow()

    private val _audioDelayMs = MutableStateFlow(0L)
    val audioDelayMs: StateFlow<Long> = _audioDelayMs.asStateFlow()

    private val _dualSubtitleEnabled = MutableStateFlow(false)
    val dualSubtitleEnabled: StateFlow<Boolean> = _dualSubtitleEnabled.asStateFlow()

    private val _subtitleEncoding = MutableStateFlow("UTF-8")
    val subtitleEncoding: StateFlow<String> = _subtitleEncoding.asStateFlow()

    private val _channelMode = MutableStateFlow("STEREO")
    val channelMode: StateFlow<String> = _channelMode.asStateFlow()

    private val _externalAudioPath = MutableStateFlow<String?>(null)
    val externalAudioPath: StateFlow<String?> = _externalAudioPath.asStateFlow()

    private val _currentSubtitleText = MutableStateFlow("")
    val currentSubtitleText: StateFlow<String> = _currentSubtitleText.asStateFlow()

    private val _currentSecondarySubtitleText = MutableStateFlow("")
    val currentSecondarySubtitleText: StateFlow<String> = _currentSecondarySubtitleText.asStateFlow()

    fun setCurrentSubtitleText(text: String) {
        _currentSubtitleText.value = text
    }

    fun setCurrentSecondarySubtitleText(text: String) {
        _currentSecondarySubtitleText.value = text
    }

    // Cached list of embedded TrackGroups for text tracks (for player override)
    private val embeddedTextGroups = mutableListOf<TrackGroup>()
    private val embeddedAudioGroups = mutableListOf<TrackGroup>()

    // â”€â”€ ISO 639 language map â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun isoLangName(code: String): String {
        val map = mapOf(
            "en" to "English",
            "ta" to "Tamil",
            "hi" to "Hindi",
            "ja" to "Japanese",
            "zh" to "Chinese",
            "ko" to "Korean",
            "fr" to "French",
            "de" to "German",
            "es" to "Spanish",
            "pt" to "Portuguese",
            "it" to "Italian",
            "ru" to "Russian",
            "ar" to "Arabic",
            "tr" to "Turkish",
            "nl" to "Dutch",
            "pl" to "Polish",
            "sv" to "Swedish",
            "da" to "Danish",
            "fi" to "Finnish",
            "no" to "Norwegian",
            "cs" to "Czech",
            "hu" to "Hungarian",
            "ro" to "Romanian",
            "uk" to "Ukrainian",
            "he" to "Hebrew",
            "th" to "Thai",
            "vi" to "Vietnamese",
            "id" to "Indonesian",
            "ms" to "Malay",
            "fa" to "Farsi/Persian",
            "bn" to "Bengali",
            "el" to "Greek",
            "ca" to "Catalan",
            "hr" to "Croatian",
            "sk" to "Slovak",
            "bg" to "Bulgarian",
            "lt" to "Lithuanian",
            "lv" to "Latvian",
            "et" to "Estonian",
            "sl" to "Slovenian"
        )
        // Handle codes like "zh-CN", "pt-BR" by normalising the prefix
        val normalised = code.lowercase().split("-", "_").firstOrNull() ?: code
        return map[normalised] ?: code.uppercase()
    }

    // â”€â”€ Channel label helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun channelLabel(count: Int): String = when (count) {
        1    -> "MONO"
        2    -> "STEREO"
        6    -> "5.1"
        8    -> "7.1"
        else -> "$count ch"
    }

    // â”€â”€ loadTracksFromPlayer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun loadTracksFromPlayer(player: Player) {
        embeddedTextGroups.clear()
        embeddedAudioGroups.clear()

        val subtitles = mutableListOf<SubtitleTrack>()
        val audios    = mutableListOf<AudioTrack>()

        val tracks = player.currentTracks
        var subtitleEmbeddedIdx = 0
        var audioIdx = 0

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_TEXT -> {
                    embeddedTextGroups.add(group.mediaTrackGroup)
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val langCode = format.language ?: "und"
                        val langName = isoLangName(langCode)
                        val desc = format.label ?: ""
                        val isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                        val isSDH = desc.contains("SDH", ignoreCase = true) ||
                                desc.contains("CC", ignoreCase = true) ||
                                desc.contains("hearing impaired", ignoreCase = true)
                        val label = buildString {
                            append(langName)
                            if (isForced) append(" [Forced]")
                            if (isSDH)    append(" [SDH]")
                            if (desc.isNotBlank() && desc != langCode) append(" â€“ $desc")
                        }
                        subtitles += SubtitleTrack(
                            index    = subtitleEmbeddedIdx++,
                            label    = label,
                            language = langCode,
                            isForced = isForced,
                            isSDH    = isSDH,
                            source   = "embedded"
                        )
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    embeddedAudioGroups.add(group.mediaTrackGroup)
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val langCode = format.language ?: "und"
                        val langName = isoLangName(langCode)
                        val codecName = format.sampleMimeType?.removePrefix("audio/") ?: "Unknown"
                        val channelsLabel = channelLabel(format.channelCount)
                        val label = buildString {
                            append(langName)
                            append(" [$codecName Â· $channelsLabel]")
                            if (!format.label.isNullOrBlank()) append(" â€“ ${format.label}")
                        }
                        audios += AudioTrack(
                            index      = audioIdx++,
                            label      = label,
                            language   = langCode,
                            codec      = codecName,
                            channels   = channelsLabel,
                            sampleRate = format.sampleRate,
                            bitrate    = format.bitrate
                        )
                    }
                }
            }
        }

        // Also scan for external subtitle files if we have a media URI
        player.currentMediaItem?.localConfiguration?.uri?.path?.let { videoPath ->
            val externalFiles = discoverExternalSubtitles(videoPath)
            externalFiles.forEachIndexed { i, file ->
                val ext = file.extension.lowercase()
                subtitles += SubtitleTrack(
                    index    = subtitleEmbeddedIdx + i,
                    label    = file.nameWithoutExtension + " [$ext]",
                    language = "und",
                    isForced = false,
                    isSDH    = false,
                    source   = "external"
                )
            }
        }

        _availableSubtitles.value  = subtitles
        _availableAudioTracks.value = audios
    }

    // â”€â”€ discoverExternalSubtitles â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun discoverExternalSubtitles(videoPath: String): List<File> {
        val extensions = listOf("srt","ass","ssa","vtt","sub","idx","ttml","dfxp","smi")
        val videoFile = File(videoPath)
        val stem = videoFile.nameWithoutExtension
        val parentDir = videoFile.parentFile ?: return emptyList()
        val subtitleSubDir = File(parentDir, "Subtitles")

        val results = mutableListOf<File>()
        val dirsToSearch = buildList {
            add(parentDir)
            if (subtitleSubDir.isDirectory) add(subtitleSubDir)
        }
        for (dir in dirsToSearch) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile &&
                    file.extension.lowercase() in extensions &&
                    file.nameWithoutExtension.startsWith(stem, ignoreCase = true)
                ) {
                    results += file
                }
            }
        }
        return results.sortedBy { it.name }
    }

    // â”€â”€ selectPrimarySubtitle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun selectPrimarySubtitle(index: Int, player: Player) {
        _primarySubtitleIndex.value = index
        if (index == -1) {
            // Disable all text tracks
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        val track = _availableSubtitles.value.getOrNull(index) ?: return
        if (track.source == "embedded") {
            // Find the matching embedded group
            val embeddedCount = _availableSubtitles.value.count { it.source == "embedded" }
            if (index < embeddedCount && index < embeddedTextGroups.size) {
                val group = embeddedTextGroups[index]
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .addOverride(TrackSelectionOverride(group, 0))
                    .build()
            }
        }
        // For "external" subtitles, the UI composable reads _primarySubtitleIndex and
        // renders the parsed cue text from loadSubtitleFile() via getCurrentCue().
    }

    // â”€â”€ selectSecondarySubtitle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun selectSecondarySubtitle(index: Int) {
        _secondarySubtitleIndex.value = index
        // External rendering only via Compose overlay using currentSecondarySubtitleText
    }

    // â”€â”€ selectAudioTrack â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun selectAudioTrack(index: Int, player: Player) {
        _activeAudioTrackIndex.value = index
        if (index < 0 || index >= embeddedAudioGroups.size) return
        val group = embeddedAudioGroups[index]
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(TrackSelectionOverride(group, 0))
            .build()
    }

    // â”€â”€ Subtitle delay / sync â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun setSubtitleDelayMs(delayMs: Long) {
        _subtitleSync.value = _subtitleSync.value.copy(delayMs = delayMs)
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        _subtitleSync.value = _subtitleSync.value.copy(
            delayMs = _subtitleSync.value.delayMs + deltaMs
        )
    }

    fun setSubtitleSpeedMultiplier(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _subtitleSync.value = _subtitleSync.value.copy(speedMultiplier = clamped)
    }

    // â”€â”€ Audio delay â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun setAudioDelayMs(delayMs: Long) {
        _audioDelayMs.value = delayMs
    }

    fun adjustAudioDelay(deltaMs: Long) {
        _audioDelayMs.value += deltaMs
    }

    // â”€â”€ Subtitle style â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun updateSubtitleStyle(update: (SubtitleStyle) -> SubtitleStyle) {
        _subtitleStyle.value = update(_subtitleStyle.value)
    }

    fun setSubtitleEncoding(charset: String) {
        _subtitleEncoding.value = charset
    }

    fun setDualSubtitleEnabled(enabled: Boolean) {
        _dualSubtitleEnabled.value = enabled
    }

    fun setChannelMode(mode: String) {
        _channelMode.value = mode
    }

    fun setExternalAudioPath(path: String?) {
        _externalAudioPath.value = path
    }

    // â”€â”€ TimedText â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    data class TimedText(val startMs: Long, val endMs: Long, val text: String)

    // â”€â”€ SRT parser â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun parseSrt(text: String, delayMs: Long, speedMult: Float): List<TimedText> {
        val result = mutableListOf<TimedText>()
        // SRT blocks are separated by blank lines
        val blocks = text.trim().split(Regex("\\r?\\n\\r?\\n"))
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 2) continue
            // First non-empty line that matches a cue number, skip it
            var lineIdx = 0
            // Skip cue index line (optional integer)
            if (lines[lineIdx].trim().matches(Regex("\\d+"))) lineIdx++
            if (lineIdx >= lines.size) continue
            // Parse timestamp line: 00:01:02,345 --> 00:01:05,678
            val tsLine = lines.getOrNull(lineIdx) ?: continue
            val tsMatch = Regex(
                """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})"""
            ).find(tsLine) ?: continue
            val (sh, sm, ss, smil, eh, em, es, emil) = tsMatch.destructured
            val rawStart = srtTimeToMs(sh.toInt(), sm.toInt(), ss.toInt(), smil.toInt())
            val rawEnd   = srtTimeToMs(eh.toInt(), em.toInt(), es.toInt(), emil.toInt())
            val (adjStart, adjEnd) = applySync(rawStart, rawEnd, delayMs, speedMult)
            lineIdx++
            val cueText = lines.drop(lineIdx).joinToString("\n").trim()
            if (cueText.isNotEmpty()) {
                result += TimedText(adjStart, adjEnd, stripTags(cueText))
            }
        }
        return result.sortedBy { it.startMs }
    }

    private fun srtTimeToMs(h: Int, m: Int, s: Int, ms: Int): Long =
        h * 3_600_000L + m * 60_000L + s * 1_000L + ms

    // â”€â”€ ASS/SSA parser â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun parseAss(text: String, delayMs: Long, speedMult: Float): List<TimedText> {
        val result = mutableListOf<TimedText>()
        var formatFields: List<String>? = null
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                val section = trimmed.removePrefix("Format:").trim()
                formatFields = section.split(",").map { it.trim() }
            }
            if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                val fields = formatFields ?: listOf(
                    "Layer","Start","End","Style","Name","MarginL","MarginR","MarginV","Effect","Text"
                )
                val values = trimmed.removePrefix("Dialogue:").trim()
                    .split(",", limit = fields.size)
                val map = fields.zip(values).toMap()
                val startStr = map["Start"] ?: continue
                val endStr   = map["End"]   ?: continue
                val rawText  = map["Text"]  ?: continue
                val rawStart = assTimeToMs(startStr.trim())
                val rawEnd   = assTimeToMs(endStr.trim())
                val (adjStart, adjEnd) = applySync(rawStart, rawEnd, delayMs, speedMult)
                val cleaned = rawText.replace(Regex("\\{[^}]*\\}"), "")  // strip override tags
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ")
                    .trim()
                if (cleaned.isNotEmpty()) {
                    result += TimedText(adjStart, adjEnd, cleaned)
                }
            }
        }
        return result.sortedBy { it.startMs }
    }

    private fun assTimeToMs(time: String): Long {
        // Format: H:MM:SS.cs  (centiseconds)
        return try {
            val parts = time.split(":")
            val h  = parts[0].toInt()
            val m  = parts[1].toInt()
            val sc = parts[2].split(".")
            val s  = sc[0].toInt()
            val cs = if (sc.size > 1) sc[1].toInt() else 0
            h * 3_600_000L + m * 60_000L + s * 1_000L + cs * 10L
        } catch (e: Exception) { 0L }
    }

    // â”€â”€ WebVTT parser â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun parseVtt(text: String, delayMs: Long, speedMult: Float): List<TimedText> {
        val result = mutableListOf<TimedText>()
        // Strip WEBVTT header
        val body = text.trim().replaceFirst(Regex("^WEBVTT[^\n]*\n"), "")
        val blocks = body.split(Regex("\\r?\\n\\r?\\n"))
        for (block in blocks) {
            val lines = block.trim().lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            var lineIdx = 0
            // Skip optional cue identifier (non-timestamp line)
            if (!lines[lineIdx].contains("-->")) lineIdx++
            val tsLine = lines.getOrNull(lineIdx) ?: continue
            val tsMatch = Regex(
                """(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})"""
            ).find(tsLine) ?: run {
                // Also try MM:SS.mmm --> MM:SS.mmm format
                val shortMatch = Regex(
                    """(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2})[,.](\d{3})"""
                ).find(tsLine)
                if (shortMatch != null) {
                    val (sm, ss, smil, em, es, emil) = shortMatch.destructured
                    val rawStart = srtTimeToMs(0, sm.toInt(), ss.toInt(), smil.toInt())
                    val rawEnd   = srtTimeToMs(0, em.toInt(), es.toInt(), emil.toInt())
                    val (adjStart, adjEnd) = applySync(rawStart, rawEnd, delayMs, speedMult)
                    lineIdx++
                    val cueText = lines.drop(lineIdx).joinToString("\n").trim()
                    if (cueText.isNotEmpty()) {
                        result += TimedText(adjStart, adjEnd, stripTags(cueText))
                    }
                }
                null
            }
            if (tsMatch != null) {
                val (sh, sm, ss, smil, eh, em, es, emil) = tsMatch.destructured
                val rawStart = srtTimeToMs(sh.toInt(), sm.toInt(), ss.toInt(), smil.toInt())
                val rawEnd   = srtTimeToMs(eh.toInt(), em.toInt(), es.toInt(), emil.toInt())
                val (adjStart, adjEnd) = applySync(rawStart, rawEnd, delayMs, speedMult)
                lineIdx++
                val cueText = lines.drop(lineIdx).joinToString("\n").trim()
                if (cueText.isNotEmpty()) {
                    result += TimedText(adjStart, adjEnd, stripTags(cueText))
                }
            }
        }
        return result.sortedBy { it.startMs }
    }

    // â”€â”€ SAMI parser â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun parseSmi(text: String, delayMs: Long, speedMult: Float): List<TimedText> {
        val result = mutableListOf<TimedText>()
        // Find all <SYNC Start=NNN> blocks
        val syncPattern = Regex("""<SYNC\s+Start=(\d+)\s*>(.*?)(?=<SYNC|</BODY|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val matches = syncPattern.findAll(text).toList()
        for (i in matches.indices) {
            val match = matches[i]
            val rawStart = match.groupValues[1].toLongOrNull() ?: continue
            val rawEnd   = if (i + 1 < matches.size)
                matches[i + 1].groupValues[1].toLongOrNull() ?: (rawStart + 5000L)
            else rawStart + 5000L
            val htmlContent = match.groupValues[2]
            // Strip HTML tags
            val cleanText = htmlContent
                .replace(Regex("<[Pp][^>]*>"), "")
                .replace(Regex("<[Bb][Rr]\\s*/?>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim()
            if (cleanText.isNotEmpty() && cleanText != "&nbsp;" && cleanText.isNotBlank()) {
                val (adjStart, adjEnd) = applySync(rawStart, rawEnd, delayMs, speedMult)
                result += TimedText(adjStart, adjEnd, cleanText)
            }
        }
        return result.sortedBy { it.startMs }
    }

    // â”€â”€ Sync helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun applySync(start: Long, end: Long, delayMs: Long, speedMult: Float): Pair<Long, Long> {
        val adjStart = ((start * speedMult).toLong() + delayMs).coerceAtLeast(0L)
        val adjEnd   = ((end   * speedMult).toLong() + delayMs).coerceAtLeast(0L)
        return adjStart to adjEnd
    }

    // â”€â”€ Strip HTML/XML tags from subtitle text â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun stripTags(text: String): String =
        text.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()

    // â”€â”€ Charset detection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun detectEncoding(bytes: ByteArray): Charset {
        // Check BOM
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return Charsets.UTF_8

        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return Charsets.UTF_16LE
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        }

        // Try UTF-8 validity heuristic
        val utf8 = runCatching { String(bytes, Charsets.UTF_8) }
        if (utf8.isSuccess && !utf8.getOrDefault("").contains('\uFFFD')) return Charsets.UTF_8

        // Try common charsets based on content heuristics
        val commonCharsets = listOf(
            "windows-1252", "ISO-8859-1", "windows-1250", "ISO-8859-2",
            "windows-1251", "KOI8-R", "windows-1256", "ISO-8859-9",
            "Shift_JIS", "EUC-KR", "GB18030"
        )
        for (name in commonCharsets) {
            val cs = runCatching { charset(name) }.getOrNull() ?: continue
            val decoded = runCatching { String(bytes, cs) }.getOrNull() ?: continue
            if (!decoded.contains('\uFFFD')) return cs
        }

        return Charsets.UTF_8   // fallback
    }

    // â”€â”€ loadSubtitleFile â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun loadSubtitleFile(file: File): List<TimedText> = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        val encoding = detectEncoding(bytes)
        // Strip BOM if present
        val text = when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> String(bytes.drop(3).toByteArray(), Charsets.UTF_8)
            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() -> String(bytes.drop(2).toByteArray(), Charsets.UTF_16LE)
            bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() -> String(bytes.drop(2).toByteArray(), Charsets.UTF_16BE)
            else -> String(bytes, encoding)
        }
        val sync = _subtitleSync.value
        when (file.extension.lowercase()) {
            "srt"             -> parseSrt(text, sync.delayMs, sync.speedMultiplier)
            "ass", "ssa"      -> parseAss(text, sync.delayMs, sync.speedMultiplier)
            "vtt"             -> parseVtt(text, sync.delayMs, sync.speedMultiplier)
            "smi"             -> parseSmi(text, sync.delayMs, sync.speedMultiplier)
            "sub"             -> parseSrt(text, sync.delayMs, sync.speedMultiplier) // MicroDVD/SRT sub
            else              -> parseSrt(text, sync.delayMs, sync.speedMultiplier) // best-effort
        }
    }

    // â”€â”€ getCurrentCue â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun getCurrentCue(cues: List<TimedText>, positionMs: Long): String {
        return cues.lastOrNull { it.startMs <= positionMs && positionMs < it.endMs }?.text ?: ""
    }

    // â”€â”€ cyclePrimarySubtitle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun cyclePrimarySubtitle() {
        val subs = _availableSubtitles.value
        if (subs.isEmpty()) {
            _primarySubtitleIndex.value = -1
            return
        }
        val current = _primarySubtitleIndex.value
        // -1 â†’ 0 â†’ 1 â†’ ... â†’ last â†’ -1 (off)
        _primarySubtitleIndex.value = if (current >= subs.lastIndex) -1 else current + 1
    }

    // â”€â”€ cycleAudioTrack â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun cycleAudioTrack(player: Player) {
        val audios = _availableAudioTracks.value
        if (audios.isEmpty()) return
        val next = (_activeAudioTrackIndex.value + 1) % audios.size
        selectAudioTrack(next, player)
    }
}

