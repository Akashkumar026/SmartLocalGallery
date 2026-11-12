package com.example.smartgallery.data.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class AppSettings(
    // ─── General & Appearance ──────────────────────────────────────────
    val themeMode: String = "SYSTEM", // "SYSTEM", "DARK", "AMOLED", "LIGHT"
    val dynamicColor: Boolean = true,
    val gridColumns: Int = 3,
    val thumbnailCropSquare: Boolean = true,
    val autoPlayGifs: Boolean = true,
    val defaultStartupTab: String = "TIMELINE", // "TIMELINE", "ALBUMS", "CLEANER", "PLAYER", "DOWNLOADER"

    // ─── AI & Smart Indexing ───────────────────────────────────────────
    val faceClusterThreshold: Float = 0.72f,
    val ocrMinChars: Int = 60,
    val duplicateJaccardThreshold: Float = 0.80f,
    val laplacianBlurThreshold: Float = 100.0f, // Sharpness variance threshold
    val bgScanSchedule: String = "REALTIME", // "REALTIME", "CHARGING_ONLY", "MANUAL"
    val excludedFoldersJson: String = "[]",

    // ─── Video & Audio Player ──────────────────────────────────────────
    val decoderPriority: String = "AUTO", // "AUTO", "HARDWARE_FIRST", "SOFTWARE_FIRST"
    val resumeBehavior: String = "ALWAYS_RESUME", // "ALWAYS_RESUME", "ALWAYS_ASK", "FROM_START"
    val doubleTapSeekSecs: Int = 10,
    val longPressSpeed: Float = 2.0f,
    val rememberBrightness: Boolean = true,
    val rememberSpeed: Boolean = false,
    val rememberAudioBoost: Boolean = false,
    val autoPipOnHome: Boolean = true,
    val backgroundAudioOnScreenOff: Boolean = false,
    val playlistEndAction: String = "PLAY_NEXT", // "PLAY_NEXT", "LOOP_CURRENT", "LOOP_ALL", "STOP"

    // ─── Subtitles & Multi-Audio ───────────────────────────────────────
    val preferredAudioLang: String = "SYSTEM",
    val preferredSubtitleLang1: String = "SYSTEM",
    val preferredSubtitleLang2: String = "NONE",
    val autoSubtitleForeign: Boolean = true,
    val subtitleFontSize: Int = 18,
    val subtitleColorArgb: Int = 0xFFFFFFFF.toInt(),
    val subtitleBgOpacity: Float = 0.6f,
    val subtitleEncoding: String = "UTF-8",
    val subtitleSaveDir: String = "",
    val subtitleFontFamily: String = "SANS",
    val subtitleOutlineColorArgb: Int = 0xFF000000.toInt(),
    val subtitleOutlineWidth: Float = 2.0f,
    val subtitleDropShadow: Boolean = true,
    val subtitleBottomMarginDp: Int = 24,
    val dualSubtitleEnabled: Boolean = false,
    val channelMode: String = "STEREO", // "STEREO", "LEFT", "RIGHT", "MONO", "SURROUND", "NIGHT"
    val subtitleDelayMs: Long = 0L,
    val audioDelayMs: Long = 0L,

    // ─── Equalizer & Audio Gain ────────────────────────────────────────
    val eqPreset: String = "FLAT",
    val eqBandGainsJson: String = "[0,0,0,0,0,0,0,0,0,0]",
    val eqPreampMb: Int = 0,
    val eqReverbPreset: Int = -1,
    val sleepTimerMinutes: Int = -1,

    // ─── Playback State Defaults ───────────────────────────────────────
    val abRepeatAMs: Long = -1L,
    val abRepeatBMs: Long = -1L,
    val aspectRatioMode: String = "FIT",
    val videoRotationDegrees: Int = 0,
    val brightnessValue: Float = 0.5f,
    val volumeBoostPercent: Float = 100f,
    val screenLocked: Boolean = false,
    val showCodecHud: Boolean = false,

    // ─── Downloader & Network Privacy Firewall ─────────────────────────
    val strictOfflineLockdown: Boolean = false, // When true, hard-blocks all network sockets and hides downloader
    val downloadDefaultResolution: String = "1080p", // "4K", "1440p", "1080p", "720p", "480p", "AUDIO_ONLY"
    val downloadDefaultAudioFormat: String = "MP3_320", // "MP3_320", "M4A_320", "FLAC"
    val downloadAria2cConnections: Int = 16,
    val downloadCustomDir: String = "",
    val cookiesTxtContent: String = "",

    // ─── Privacy, Vault & File Management ──────────────────────────────
    val vaultPinHash: String = "",
    val vaultBiometricEnabled: Boolean = false,
    val trashAutoPurgeDays: Int = 30,
    val stripGpsExifBeforeShare: Boolean = true,
    val photoScannerEnhanceContrast: Float = 1.5f
)

object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val GRID_COLUMNS = intPreferencesKey("grid_columns")
    val THUMBNAIL_CROP_SQUARE = booleanPreferencesKey("thumbnail_crop_square")
    val AUTO_PLAY_GIFS = booleanPreferencesKey("auto_play_gifs")
    val DEFAULT_STARTUP_TAB = stringPreferencesKey("default_startup_tab")

    val FACE_CLUSTER_THRESHOLD = floatPreferencesKey("face_cluster_threshold")
    val OCR_MIN_CHARS = intPreferencesKey("ocr_min_chars")
    val DUPLICATE_JACCARD_THRESHOLD = floatPreferencesKey("duplicate_jaccard_threshold")
    val LAPLACIAN_BLUR_THRESHOLD = floatPreferencesKey("laplacian_blur_threshold")
    val BG_SCAN_SCHEDULE = stringPreferencesKey("bg_scan_schedule")
    val EXCLUDED_FOLDERS_JSON = stringPreferencesKey("excluded_folders_json")

    val DECODER_PRIORITY = stringPreferencesKey("decoder_priority")
    val RESUME_BEHAVIOR = stringPreferencesKey("resume_behavior")
    val DOUBLE_TAP_SEEK_SECS = intPreferencesKey("double_tap_seek_secs")
    val LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")
    val REMEMBER_BRIGHTNESS = booleanPreferencesKey("remember_brightness")
    val REMEMBER_SPEED = booleanPreferencesKey("remember_speed")
    val REMEMBER_AUDIO_BOOST = booleanPreferencesKey("remember_audio_boost")
    val AUTO_PIP_ON_HOME = booleanPreferencesKey("auto_pip_on_home")
    val BACKGROUND_AUDIO_ON_SCREEN_OFF = booleanPreferencesKey("background_audio_on_screen_off")
    val PLAYLIST_END_ACTION = stringPreferencesKey("playlist_end_action")

    val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
    val PREFERRED_SUBTITLE_LANG1 = stringPreferencesKey("preferred_subtitle_lang1")
    val PREFERRED_SUBTITLE_LANG2 = stringPreferencesKey("preferred_subtitle_lang2")
    val AUTO_SUBTITLE_FOREIGN = booleanPreferencesKey("auto_subtitle_foreign")
    val SUBTITLE_FONT_SIZE = intPreferencesKey("subtitle_font_size")
    val SUBTITLE_COLOR_ARGB = intPreferencesKey("subtitle_color_argb")
    val SUBTITLE_BG_OPACITY = floatPreferencesKey("subtitle_bg_opacity")
    val SUBTITLE_ENCODING = stringPreferencesKey("subtitle_encoding")
    val SUBTITLE_SAVE_DIR = stringPreferencesKey("subtitle_save_dir")
    val SUBTITLE_FONT_FAMILY = stringPreferencesKey("subtitle_font_family")
    val SUBTITLE_OUTLINE_COLOR_ARGB = intPreferencesKey("subtitle_outline_color_argb")
    val SUBTITLE_OUTLINE_WIDTH = floatPreferencesKey("subtitle_outline_width")
    val SUBTITLE_DROP_SHADOW = booleanPreferencesKey("subtitle_drop_shadow")
    val SUBTITLE_BOTTOM_MARGIN_DP = intPreferencesKey("subtitle_bottom_margin_dp")
    val DUAL_SUBTITLE_ENABLED = booleanPreferencesKey("dual_subtitle_enabled")
    val CHANNEL_MODE = stringPreferencesKey("channel_mode")
    val SUBTITLE_DELAY_MS = longPreferencesKey("subtitle_delay_ms")
    val AUDIO_DELAY_MS = longPreferencesKey("audio_delay_ms")

    val EQ_PRESET = stringPreferencesKey("eq_preset")
    val EQ_BAND_GAINS_JSON = stringPreferencesKey("eq_band_gains_json")
    val EQ_PREAMP_MB = intPreferencesKey("eq_preamp_mb")
    val EQ_REVERB_PRESET = intPreferencesKey("eq_reverb_preset")
    val SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")

    val AB_REPEAT_A_MS = longPreferencesKey("ab_repeat_a_ms")
    val AB_REPEAT_B_MS = longPreferencesKey("ab_repeat_b_ms")
    val ASPECT_RATIO_MODE = stringPreferencesKey("aspect_ratio_mode")
    val VIDEO_ROTATION_DEGREES = intPreferencesKey("video_rotation_degrees")
    val BRIGHTNESS_VALUE = floatPreferencesKey("brightness_value")
    val VOLUME_BOOST_PERCENT = floatPreferencesKey("volume_boost_percent")
    val SCREEN_LOCKED = booleanPreferencesKey("screen_locked")
    val SHOW_CODEC_HUD = booleanPreferencesKey("show_codec_hud")

    val STRICT_OFFLINE_LOCKDOWN = booleanPreferencesKey("strict_offline_lockdown")
    val DOWNLOAD_DEFAULT_RESOLUTION = stringPreferencesKey("download_default_resolution")
    val DOWNLOAD_DEFAULT_AUDIO_FORMAT = stringPreferencesKey("download_default_audio_format")
    val DOWNLOAD_ARIA2C_CONNECTIONS = intPreferencesKey("download_aria2c_connections")
    val DOWNLOAD_CUSTOM_DIR = stringPreferencesKey("download_custom_dir")
    val COOKIES_TXT_CONTENT = stringPreferencesKey("cookies_txt_content")

    val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
    val VAULT_BIOMETRIC_ENABLED = booleanPreferencesKey("vault_biometric_enabled")
    val TRASH_AUTO_PURGE_DAYS = intPreferencesKey("trash_auto_purge_days")
    val STRIP_GPS_EXIF_BEFORE_SHARE = booleanPreferencesKey("strip_gps_exif_before_share")
    val PHOTO_SCANNER_ENHANCE_CONTRAST = floatPreferencesKey("photo_scanner_enhance_contrast")
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_gallery_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppSettings(
                themeMode = prefs[SettingsKeys.THEME_MODE] ?: "SYSTEM",
                dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR] ?: true,
                gridColumns = prefs[SettingsKeys.GRID_COLUMNS] ?: 3,
                thumbnailCropSquare = prefs[SettingsKeys.THUMBNAIL_CROP_SQUARE] ?: true,
                autoPlayGifs = prefs[SettingsKeys.AUTO_PLAY_GIFS] ?: true,
                defaultStartupTab = prefs[SettingsKeys.DEFAULT_STARTUP_TAB] ?: "TIMELINE",
                faceClusterThreshold = prefs[SettingsKeys.FACE_CLUSTER_THRESHOLD] ?: 0.72f,
                ocrMinChars = prefs[SettingsKeys.OCR_MIN_CHARS] ?: 60,
                duplicateJaccardThreshold = prefs[SettingsKeys.DUPLICATE_JACCARD_THRESHOLD] ?: 0.80f,
                laplacianBlurThreshold = prefs[SettingsKeys.LAPLACIAN_BLUR_THRESHOLD] ?: 100.0f,
                bgScanSchedule = prefs[SettingsKeys.BG_SCAN_SCHEDULE] ?: "REALTIME",
                excludedFoldersJson = prefs[SettingsKeys.EXCLUDED_FOLDERS_JSON] ?: "[]",
                decoderPriority = prefs[SettingsKeys.DECODER_PRIORITY] ?: "AUTO",
                resumeBehavior = prefs[SettingsKeys.RESUME_BEHAVIOR] ?: "ALWAYS_RESUME",
                doubleTapSeekSecs = prefs[SettingsKeys.DOUBLE_TAP_SEEK_SECS] ?: 10,
                longPressSpeed = prefs[SettingsKeys.LONG_PRESS_SPEED] ?: 2.0f,
                rememberBrightness = prefs[SettingsKeys.REMEMBER_BRIGHTNESS] ?: true,
                rememberSpeed = prefs[SettingsKeys.REMEMBER_SPEED] ?: false,
                rememberAudioBoost = prefs[SettingsKeys.REMEMBER_AUDIO_BOOST] ?: false,
                autoPipOnHome = prefs[SettingsKeys.AUTO_PIP_ON_HOME] ?: true,
                backgroundAudioOnScreenOff = prefs[SettingsKeys.BACKGROUND_AUDIO_ON_SCREEN_OFF] ?: false,
                playlistEndAction = prefs[SettingsKeys.PLAYLIST_END_ACTION] ?: "PLAY_NEXT",
                preferredAudioLang = prefs[SettingsKeys.PREFERRED_AUDIO_LANG] ?: "SYSTEM",
                preferredSubtitleLang1 = prefs[SettingsKeys.PREFERRED_SUBTITLE_LANG1] ?: "SYSTEM",
                preferredSubtitleLang2 = prefs[SettingsKeys.PREFERRED_SUBTITLE_LANG2] ?: "NONE",
                autoSubtitleForeign = prefs[SettingsKeys.AUTO_SUBTITLE_FOREIGN] ?: true,
                subtitleFontSize = prefs[SettingsKeys.SUBTITLE_FONT_SIZE] ?: 18,
                subtitleColorArgb = prefs[SettingsKeys.SUBTITLE_COLOR_ARGB] ?: 0xFFFFFFFF.toInt(),
                subtitleBgOpacity = prefs[SettingsKeys.SUBTITLE_BG_OPACITY] ?: 0.6f,
                subtitleEncoding = prefs[SettingsKeys.SUBTITLE_ENCODING] ?: "UTF-8",
                subtitleSaveDir = prefs[SettingsKeys.SUBTITLE_SAVE_DIR] ?: "",
                subtitleFontFamily = prefs[SettingsKeys.SUBTITLE_FONT_FAMILY] ?: "SANS",
                subtitleOutlineColorArgb = prefs[SettingsKeys.SUBTITLE_OUTLINE_COLOR_ARGB] ?: 0xFF000000.toInt(),
                subtitleOutlineWidth = prefs[SettingsKeys.SUBTITLE_OUTLINE_WIDTH] ?: 2.0f,
                subtitleDropShadow = prefs[SettingsKeys.SUBTITLE_DROP_SHADOW] ?: true,
                subtitleBottomMarginDp = prefs[SettingsKeys.SUBTITLE_BOTTOM_MARGIN_DP] ?: 24,
                dualSubtitleEnabled = prefs[SettingsKeys.DUAL_SUBTITLE_ENABLED] ?: false,
                channelMode = prefs[SettingsKeys.CHANNEL_MODE] ?: "STEREO",
                subtitleDelayMs = prefs[SettingsKeys.SUBTITLE_DELAY_MS] ?: 0L,
                audioDelayMs = prefs[SettingsKeys.AUDIO_DELAY_MS] ?: 0L,
                eqPreset = prefs[SettingsKeys.EQ_PRESET] ?: "FLAT",
                eqBandGainsJson = prefs[SettingsKeys.EQ_BAND_GAINS_JSON] ?: "[0,0,0,0,0,0,0,0,0,0]",
                eqPreampMb = prefs[SettingsKeys.EQ_PREAMP_MB] ?: 0,
                eqReverbPreset = prefs[SettingsKeys.EQ_REVERB_PRESET] ?: -1,
                sleepTimerMinutes = prefs[SettingsKeys.SLEEP_TIMER_MINUTES] ?: -1,
                abRepeatAMs = prefs[SettingsKeys.AB_REPEAT_A_MS] ?: -1L,
                abRepeatBMs = prefs[SettingsKeys.AB_REPEAT_B_MS] ?: -1L,
                aspectRatioMode = prefs[SettingsKeys.ASPECT_RATIO_MODE] ?: "FIT",
                videoRotationDegrees = prefs[SettingsKeys.VIDEO_ROTATION_DEGREES] ?: 0,
                brightnessValue = prefs[SettingsKeys.BRIGHTNESS_VALUE] ?: 0.5f,
                volumeBoostPercent = prefs[SettingsKeys.VOLUME_BOOST_PERCENT] ?: 100f,
                screenLocked = prefs[SettingsKeys.SCREEN_LOCKED] ?: false,
                showCodecHud = prefs[SettingsKeys.SHOW_CODEC_HUD] ?: false,
                strictOfflineLockdown = prefs[SettingsKeys.STRICT_OFFLINE_LOCKDOWN] ?: false,
                downloadDefaultResolution = prefs[SettingsKeys.DOWNLOAD_DEFAULT_RESOLUTION] ?: "1080p",
                downloadDefaultAudioFormat = prefs[SettingsKeys.DOWNLOAD_DEFAULT_AUDIO_FORMAT] ?: "MP3_320",
                downloadAria2cConnections = prefs[SettingsKeys.DOWNLOAD_ARIA2C_CONNECTIONS] ?: 16,
                downloadCustomDir = prefs[SettingsKeys.DOWNLOAD_CUSTOM_DIR] ?: "",
                cookiesTxtContent = prefs[SettingsKeys.COOKIES_TXT_CONTENT] ?: "",
                vaultPinHash = prefs[SettingsKeys.VAULT_PIN_HASH] ?: "",
                vaultBiometricEnabled = prefs[SettingsKeys.VAULT_BIOMETRIC_ENABLED] ?: false,
                trashAutoPurgeDays = prefs[SettingsKeys.TRASH_AUTO_PURGE_DAYS] ?: 30,
                stripGpsExifBeforeShare = prefs[SettingsKeys.STRIP_GPS_EXIF_BEFORE_SHARE] ?: true,
                photoScannerEnhanceContrast = prefs[SettingsKeys.PHOTO_SCANNER_ENHANCE_CONTRAST] ?: 1.5f
            )
        }

    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[SettingsKeys.THEME_MODE] = mode }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[SettingsKeys.DYNAMIC_COLOR] = enabled }
    suspend fun setGridColumns(cols: Int) = context.dataStore.edit { it[SettingsKeys.GRID_COLUMNS] = cols.coerceIn(2, 6) }
    suspend fun setThumbnailCropSquare(square: Boolean) = context.dataStore.edit { it[SettingsKeys.THUMBNAIL_CROP_SQUARE] = square }
    suspend fun setAutoPlayGifs(enabled: Boolean) = context.dataStore.edit { it[SettingsKeys.AUTO_PLAY_GIFS] = enabled }
    suspend fun setDefaultStartupTab(tab: String) = context.dataStore.edit { it[SettingsKeys.DEFAULT_STARTUP_TAB] = tab }

    suspend fun setFaceClusterThreshold(t: Float) = context.dataStore.edit { it[SettingsKeys.FACE_CLUSTER_THRESHOLD] = t.coerceIn(0.65f, 0.85f) }
    suspend fun setOcrMinChars(n: Int) = context.dataStore.edit { it[SettingsKeys.OCR_MIN_CHARS] = n.coerceIn(30, 150) }
    suspend fun setDuplicateJaccardThreshold(t: Float) = context.dataStore.edit { it[SettingsKeys.DUPLICATE_JACCARD_THRESHOLD] = t.coerceIn(0.60f, 1.00f) }
    suspend fun setLaplacianBlurThreshold(t: Float) = context.dataStore.edit { it[SettingsKeys.LAPLACIAN_BLUR_THRESHOLD] = t.coerceAtLeast(10.0f) }
    suspend fun setBgScanSchedule(s: String) = context.dataStore.edit { it[SettingsKeys.BG_SCAN_SCHEDULE] = s }
    suspend fun setExcludedFolders(folders: List<String>) = context.dataStore.edit {
        val array = JSONArray()
        folders.forEach { f -> array.put(f) }
        it[SettingsKeys.EXCLUDED_FOLDERS_JSON] = array.toString()
    }
    suspend fun updateExcludedFolders(folders: List<String>) = setExcludedFolders(folders)

    suspend fun setDecoderPriority(p: String) = context.dataStore.edit { it[SettingsKeys.DECODER_PRIORITY] = p }
    suspend fun setResumeBehavior(b: String) = context.dataStore.edit { it[SettingsKeys.RESUME_BEHAVIOR] = b }
    suspend fun setDoubleTapSeekSecs(s: Int) = context.dataStore.edit { it[SettingsKeys.DOUBLE_TAP_SEEK_SECS] = s }
    suspend fun setLongPressSpeed(speed: Float) = context.dataStore.edit { it[SettingsKeys.LONG_PRESS_SPEED] = speed }
    suspend fun setRememberBrightness(b: Boolean) = context.dataStore.edit { it[SettingsKeys.REMEMBER_BRIGHTNESS] = b }
    suspend fun setRememberSpeed(b: Boolean) = context.dataStore.edit { it[SettingsKeys.REMEMBER_SPEED] = b }
    suspend fun setRememberAudioBoost(b: Boolean) = context.dataStore.edit { it[SettingsKeys.REMEMBER_AUDIO_BOOST] = b }
    suspend fun setAutoPipOnHome(b: Boolean) = context.dataStore.edit { it[SettingsKeys.AUTO_PIP_ON_HOME] = b }
    suspend fun setBackgroundAudioOnScreenOff(b: Boolean) = context.dataStore.edit { it[SettingsKeys.BACKGROUND_AUDIO_ON_SCREEN_OFF] = b }
    suspend fun setPlaylistEndAction(action: String) = context.dataStore.edit { it[SettingsKeys.PLAYLIST_END_ACTION] = action }

    suspend fun setPreferredAudioLang(lang: String) = context.dataStore.edit { it[SettingsKeys.PREFERRED_AUDIO_LANG] = lang }
    suspend fun setPreferredSubtitleLang1(lang: String) = context.dataStore.edit { it[SettingsKeys.PREFERRED_SUBTITLE_LANG1] = lang }
    suspend fun setPreferredSubtitleLang2(lang: String) = context.dataStore.edit { it[SettingsKeys.PREFERRED_SUBTITLE_LANG2] = lang }
    suspend fun setAutoSubtitleForeign(b: Boolean) = context.dataStore.edit { it[SettingsKeys.AUTO_SUBTITLE_FOREIGN] = b }
    suspend fun setSubtitleFontSize(size: Int) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_FONT_SIZE] = size.coerceIn(10, 42) }
    suspend fun setSubtitleColorArgb(color: Int) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_COLOR_ARGB] = color }
    suspend fun setSubtitleBgOpacity(opacity: Float) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_BG_OPACITY] = opacity.coerceIn(0f, 1f) }
    suspend fun setSubtitleEncoding(enc: String) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_ENCODING] = enc }
    suspend fun setSubtitleSaveDir(dir: String) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_SAVE_DIR] = dir }
    suspend fun setSubtitleFontFamily(font: String) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_FONT_FAMILY] = font }
    suspend fun setSubtitleOutlineColorArgb(color: Int) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_OUTLINE_COLOR_ARGB] = color }
    suspend fun setSubtitleOutlineWidth(w: Float) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_OUTLINE_WIDTH] = w }
    suspend fun setSubtitleDropShadow(enabled: Boolean) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_DROP_SHADOW] = enabled }
    suspend fun setSubtitleBottomMarginDp(dp: Int) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_BOTTOM_MARGIN_DP] = dp }
    suspend fun setDualSubtitleEnabled(enabled: Boolean) = context.dataStore.edit { it[SettingsKeys.DUAL_SUBTITLE_ENABLED] = enabled }
    suspend fun setChannelMode(mode: String) = context.dataStore.edit { it[SettingsKeys.CHANNEL_MODE] = mode }
    suspend fun setSubtitleDelayMs(ms: Long) = context.dataStore.edit { it[SettingsKeys.SUBTITLE_DELAY_MS] = ms }
    suspend fun setAudioDelayMs(ms: Long) = context.dataStore.edit { it[SettingsKeys.AUDIO_DELAY_MS] = ms }

    suspend fun setEqPreset(preset: String) = context.dataStore.edit { it[SettingsKeys.EQ_PRESET] = preset }
    suspend fun setEqBandGains(gains: IntArray) = context.dataStore.edit {
        val array = JSONArray()
        gains.forEach { array.put(it) }
        it[SettingsKeys.EQ_BAND_GAINS_JSON] = array.toString()
    }
    suspend fun setEqBandGainsJson(json: String) = context.dataStore.edit {
        it[SettingsKeys.EQ_BAND_GAINS_JSON] = json
    }
    suspend fun setEqPreampMb(mb: Int) = context.dataStore.edit { it[SettingsKeys.EQ_PREAMP_MB] = mb }
    suspend fun setEqReverbPreset(preset: Int) = context.dataStore.edit { it[SettingsKeys.EQ_REVERB_PRESET] = preset }
    suspend fun setSleepTimerMinutes(mins: Int) = context.dataStore.edit { it[SettingsKeys.SLEEP_TIMER_MINUTES] = mins }

    suspend fun setAbRepeatA(ms: Long) = context.dataStore.edit { it[SettingsKeys.AB_REPEAT_A_MS] = ms }
    suspend fun setAbRepeatB(ms: Long) = context.dataStore.edit { it[SettingsKeys.AB_REPEAT_B_MS] = ms }
    suspend fun setAspectRatioMode(mode: String) = context.dataStore.edit { it[SettingsKeys.ASPECT_RATIO_MODE] = mode }
    suspend fun setVideoRotationDegrees(deg: Int) = context.dataStore.edit { it[SettingsKeys.VIDEO_ROTATION_DEGREES] = deg }
    suspend fun setBrightnessValue(v: Float) = context.dataStore.edit { it[SettingsKeys.BRIGHTNESS_VALUE] = v }
    suspend fun setVolumeBoostPercent(p: Float) = context.dataStore.edit { it[SettingsKeys.VOLUME_BOOST_PERCENT] = p }
    suspend fun setScreenLocked(locked: Boolean) = context.dataStore.edit { it[SettingsKeys.SCREEN_LOCKED] = locked }
    suspend fun setShowCodecHud(show: Boolean) = context.dataStore.edit { it[SettingsKeys.SHOW_CODEC_HUD] = show }

    // Downloader & Strict Privacy Isolation
    suspend fun setStrictOfflineLockdown(enabled: Boolean) = context.dataStore.edit { it[SettingsKeys.STRICT_OFFLINE_LOCKDOWN] = enabled }
    suspend fun setDownloadDefaultResolution(res: String) = context.dataStore.edit { it[SettingsKeys.DOWNLOAD_DEFAULT_RESOLUTION] = res }
    suspend fun setDownloadDefaultAudioFormat(fmt: String) = context.dataStore.edit { it[SettingsKeys.DOWNLOAD_DEFAULT_AUDIO_FORMAT] = fmt }
    suspend fun setDownloadAria2cConnections(conn: Int) = context.dataStore.edit { it[SettingsKeys.DOWNLOAD_ARIA2C_CONNECTIONS] = conn.coerceIn(1, 16) }
    suspend fun setDownloadCustomDir(dir: String) = context.dataStore.edit { it[SettingsKeys.DOWNLOAD_CUSTOM_DIR] = dir }
    suspend fun setCookiesTxtContent(content: String) = context.dataStore.edit { it[SettingsKeys.COOKIES_TXT_CONTENT] = content }

    // Vault & File Tools
    suspend fun setVaultPinHash(hash: String) = context.dataStore.edit { it[SettingsKeys.VAULT_PIN_HASH] = hash }
    suspend fun setVaultBiometricEnabled(enabled: Boolean) = context.dataStore.edit { it[SettingsKeys.VAULT_BIOMETRIC_ENABLED] = enabled }
    suspend fun setTrashAutoPurgeDays(days: Int) = context.dataStore.edit { it[SettingsKeys.TRASH_AUTO_PURGE_DAYS] = days.coerceIn(1, 90) }
    suspend fun setStripGpsExifBeforeShare(strip: Boolean) = context.dataStore.edit { it[SettingsKeys.STRIP_GPS_EXIF_BEFORE_SHARE] = strip }
    suspend fun setPhotoScannerEnhanceContrast(c: Float) = context.dataStore.edit { it[SettingsKeys.PHOTO_SCANNER_ENHANCE_CONTRAST] = c }

    suspend fun exportSettingsJson(): String {
        val json = JSONObject()
        val prefs = context.dataStore.data.first()
        prefs.asMap().forEach { (key, value) ->
            if (key.name != SettingsKeys.VAULT_PIN_HASH.name) {
                json.put(key.name, value)
            }
        }
        return json.toString(2)
    }

    suspend fun importSettingsJson(jsonStr: String) {
        val json = JSONObject(jsonStr)
        context.dataStore.edit { prefs ->
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == SettingsKeys.VAULT_PIN_HASH.name) continue
                when (val value = json.get(key)) {
                    is Boolean -> prefs[booleanPreferencesKey(key)] = value
                    is Int -> prefs[intPreferencesKey(key)] = value
                    is Double -> prefs[floatPreferencesKey(key)] = value.toFloat()
                    is Long -> prefs[longPreferencesKey(key)] = value
                    is String -> prefs[stringPreferencesKey(key)] = value
                }
            }
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}

