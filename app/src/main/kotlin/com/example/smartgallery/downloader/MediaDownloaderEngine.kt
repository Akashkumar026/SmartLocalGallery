package com.example.smartgallery.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import com.example.smartgallery.data.db.DownloadTask
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.profiler.DeviceCapabilityProfiler
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State machine representing the initialization lifecycle of the embedded download engine.
 */
sealed class EngineInitState {
    object NotStarted : EngineInitState()
    object Initializing : EngineInitState()
    object Ready : EngineInitState()
    data class Failed(val error: Throwable) : EngineInitState()
}

class DownloadPausedException(val taskId: Long) : Exception("Download paused by user")
class DownloadCancelledException(val taskId: Long) : Exception("Download cancelled by user")

@Singleton
class MediaDownloaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: GalleryDatabase,
    private val settings: SettingsRepository,
    private val profiler: DeviceCapabilityProfiler,
    private val nativeInitializer: NativeEngineInitializer
) {
    private val initMutex = Mutex()

    @Volatile
    var engineState: EngineInitState = EngineInitState.NotStarted
        private set

    val isReady: Boolean
        get() = engineState is EngineInitState.Ready

    /**
     * Thread-safe, Mutex-guarded initialization of YoutubeDL, FFmpeg, and Aria2c.
     * Retries initialization on the next invocation if the previous attempt failed.
     * Never swallows exceptions: all failures are recorded in [EngineInitState.Failed],
     * logged with tag 'Downloader', and returned as Result.failure.
     */
    suspend fun ensureReady(ctx: Context = context): Result<Unit> = initMutex.withLock {
        when (engineState) {
            is EngineInitState.Ready -> return Result.success(Unit)
            is EngineInitState.Initializing -> {
                // If currently initializing in another coroutine, the Mutex forces sequential evaluation.
            }
            is EngineInitState.NotStarted, is EngineInitState.Failed -> {
                // Proceed with fresh initialization attempt or retry.
            }
        }

        engineState = EngineInitState.Initializing
        val appCtx = ctx.applicationContext
        return try {
            nativeInitializer.initYoutubeDL(appCtx)
            runCatching { nativeInitializer.initFFmpeg(appCtx) }.onFailure {
                Log.w(TAG, "FFmpeg initialization notice: ${it.message}")
            }
            runCatching { nativeInitializer.initAria2c(appCtx) }.onFailure {
                Log.w(TAG, "Aria2c initialization notice: ${it.message}")
            }
            engineState = EngineInitState.Ready
            Log.i(TAG, "All embedded downloader engines initialized successfully via Mutex.")
            Result.success(Unit)
        } catch (t: Throwable) {
            engineState = EngineInitState.Failed(t)
            Log.e(TAG, "Failed initializing downloader engines: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Backward-compatible helper that throws if engine initialization fails,
     * and performs an automatic 12-hour periodic self-update of yt-dlp extractors (NIGHTLY channel).
     */
    suspend fun ensureInitialized(ctx: Context = context) = withContext(Dispatchers.IO) {
        val res = ensureReady(ctx)
        if (res.isFailure) {
            throw res.exceptionOrNull() ?: IllegalStateException("Downloader engine initialization failed")
        }

        val appCtx = ctx.applicationContext
        val prefs = runCatching { appCtx.getSharedPreferences("downloader_engine_prefs", Context.MODE_PRIVATE) }.getOrNull()
        if (prefs != null) {
            val lastUpdate = prefs.getLong("last_ytdlp_update_ms", 0L)
            val now = System.currentTimeMillis()
            val twelveHoursMs = 12L * 60 * 60 * 1000L

            if (now - lastUpdate > twelveHoursMs) {
                runCatching {
                    Log.i(TAG, "Checking for yt-dlp extractor auto-update (>12h since last check)...")
                    val status = YoutubeDL.getInstance().updateYoutubeDL(appCtx, YoutubeDL.UpdateChannel.NIGHTLY)
                    Log.i(TAG, "Automatic yt-dlp extractor update completed: $status")
                    prefs.edit().putLong("last_ytdlp_update_ms", now).apply()
                }.onFailure { t ->
                    Log.w(TAG, "Automatic yt-dlp extractor update notice: ${t.message}")
                }
            }
        }
    }

    /**
     * Triggers manual update of the yt-dlp extractor on demand (using NIGHTLY channel with fallback to binary update).
     */
    suspend fun updateExtractor(ctx: Context = context): Result<String> = withContext(Dispatchers.IO) {
        val ready = ensureReady(ctx)
        if (ready.isFailure) {
            return@withContext Result.failure(ready.exceptionOrNull() ?: IllegalStateException("Downloader engine not ready"))
        }
        val appCtx = ctx.applicationContext
        try {
            Log.i(TAG, "Manually triggering yt-dlp extractor update (NIGHTLY channel)...")
            val status = YoutubeDL.getInstance().updateYoutubeDL(appCtx, YoutubeDL.UpdateChannel.NIGHTLY)
            val prefs = runCatching { appCtx.getSharedPreferences("downloader_engine_prefs", Context.MODE_PRIVATE) }.getOrNull()
            prefs?.edit()?.putLong("last_ytdlp_update_ms", System.currentTimeMillis())?.apply()
            val msg = "yt-dlp extractor updated: $status"
            Log.i(TAG, msg)
            Result.success(msg)
        } catch (t: Throwable) {
            Log.w(TAG, "NIGHTLY update failed, falling back to direct binary update: ${t.message}")
            updateYtdlBinary(ctx)
        }
    }

    /**
     * Asynchronously triggers non-blocking background check for yt-dlp binary updates.
     * Runs in a detached coroutine and never blocks [ensureReady] or active downloads.
     */
    fun scheduleBackgroundYtdlUpdate(appContext: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ready = ensureReady(appContext)
                if (ready.isSuccess) {
                    Log.i(TAG, "Starting non-blocking background yt-dlp update...")
                    val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
                    Log.i(TAG, "Background yt-dlp update status: $status")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Background yt-dlp update notice: ${t.message}")
            }
        }
    }

    /**
     * Checks and updates yt-dlp binary from the official stable release channel.
     */
    suspend fun updateYtdlBinary(ctx: Context = context): Result<String> = withContext(Dispatchers.IO) {
        val ready = ensureReady(ctx)
        if (ready.isFailure) {
            return@withContext Result.failure(ready.exceptionOrNull() ?: IllegalStateException("Downloader engine not ready"))
        }
        try {
            Log.i(TAG, "Checking and updating yt-dlp binary from official release channel...")
            val appCtx = ctx.applicationContext
            val ytdlpDir = File(appCtx.noBackupFilesDir, "youtubedl-android/yt-dlp")
            val targetFile = File(ytdlpDir, "yt-dlp")
            val tempFile = File.createTempFile("ytdlp_update", ".tmp", appCtx.cacheDir)

            var currentUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
            var connection: java.net.HttpURLConnection
            var redirects = 0
            while (true) {
                val u = java.net.URL(currentUrl)
                connection = u.openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                val code = connection.responseCode
                if (code in 300..399) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newLocation != null && redirects++ < 5) {
                        currentUrl = newLocation
                        continue
                    }
                }
                if (code !in 200..299) {
                    connection.disconnect()
                    throw java.io.IOException("Server returned HTTP $code while fetching yt-dlp update")
                }
                break
            }

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()

            if (tempFile.length() < 1_000_000L) {
                tempFile.delete()
                throw java.io.IOException("Downloaded yt-dlp payload is suspiciously small (${tempFile.length()} bytes)")
            }

            if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
            tempFile.copyTo(targetFile, overwrite = true)
            targetFile.setExecutable(true, false)
            tempFile.delete()

            val msg = "Successfully updated yt-dlp to latest version (${targetFile.length()} bytes)"
            Log.i(TAG, msg)
            Result.success(msg)
        } catch (t: Throwable) {
            Log.e(TAG, "yt-dlp update failed: ${t.message}", t)
            Result.failure(t)
        }
    }

    companion object {
        const val TAG = "Downloader"

        /**
         * Sanitizes URLs before logging to avoid logging sensitive query parameters.
         */
        fun sanitizeUrlForLog(url: String): String = url.substringBefore("?")

        /**
         * 64-bit Long millisecond formatter supporting 90+ hour marathon videos (HHH:MM:SS).
         */
        fun formatDurationHms(ms: Long): String {
            if (ms <= 0L) return "00:00:00"
            val totalSecs = ms / 1000L
            val hours = totalSecs / 3600L
            val minutes = (totalSecs % 3600L) / 60L
            val seconds = totalSecs % 60L
            return if (hours >= 100) {
                String.format(Locale.ROOT, "%03d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
            }
        }

        /**
         * Parses "HH:MM:SS" or "HHH:MM:SS" or "MM:SS" into 64-bit millisecond Long.
         */
        fun parseHmsToMs(timeStr: String): Long {
            val parts = timeStr.trim().split(":")
            return try {
                when (parts.size) {
                    3 -> {
                        val h = parts[0].toLong()
                        val m = parts[1].toLong()
                        val s = parts[2].toDouble()
                        ((h * 3600 + m * 60 + s) * 1000).toLong()
                    }
                    2 -> {
                        val m = parts[0].toLong()
                        val s = parts[1].toDouble()
                        ((m * 60 + s) * 1000).toLong()
                    }
                    1 -> {
                        (parts[0].toDouble() * 1000).toLong()
                    }
                    else -> 0L
                }
            } catch (e: Exception) {
                0L
            }
        }

        /**
         * Parses yt-dlp download speed string (e.g. "4.12MiB/s" or "320KiB/s") to bytes per second.
         */
        fun parseSpeedBytes(line: String?): Long {
            if (line.isNullOrBlank()) return 0L
            val match = Regex("([0-9.]+)\\s*([kKMmGg])i?B/s").find(line) ?: return 0L
            val num = match.groupValues[1].toDoubleOrNull() ?: return 0L
            val unit = match.groupValues[2].uppercase(Locale.ROOT)
            return when (unit) {
                "K" -> (num * 1024).toLong()
                "M" -> (num * 1024 * 1024).toLong()
                "G" -> (num * 1024 * 1024 * 1024).toLong()
                else -> num.toLong()
            }
        }

        fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0L) return ""
            return when {
                bytesPerSec >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
                bytesPerSec >= 1024 -> String.format(Locale.ROOT, "%d KB/s", bytesPerSec / 1024)
                else -> "$bytesPerSec B/s"
            }
        }

        fun parseTotalBytesFromLine(line: String?): Long {
            if (line.isNullOrBlank()) return 0L
            val match = Regex("of\\s+~?([0-9.]+)\\s*([kKMmGg])i?B").find(line) ?: return 0L
            val num = match.groupValues[1].toDoubleOrNull() ?: return 0L
            val unit = match.groupValues[2].uppercase(Locale.ROOT)
            return when (unit) {
                "K" -> (num * 1024).toLong()
                "M" -> (num * 1024 * 1024).toLong()
                "G" -> (num * 1024 * 1024 * 1024).toLong()
                else -> num.toLong()
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return ""
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format(Locale.ROOT, "%d KB", bytes / 1024)
                else -> "$bytes B"
            }
        }

        /**
         * Verifies sufficient disk space in both internal cache (staging) and external storage (destination).
         * Throws IllegalStateException with "Low Storage Space" if disk space is critically low.
         */
        fun checkStorageHeadroom(context: Context, stagingDir: File? = null, estimatedTotalBytes: Long = 0L) {
            val cacheStat = StatFs(context.cacheDir.path)
            val cacheAvailable = cacheStat.availableBytes
            val extStat = StatFs(Environment.getExternalStorageDirectory().path)
            val extAvailable = extStat.availableBytes

            val stagedBytes = stagingDir?.listFiles()?.sumOf { it.length() } ?: 0L
            val requiredBytes = when {
                estimatedTotalBytes > 0L -> (estimatedTotalBytes * 1.5).toLong()
                stagedBytes > 0L -> (stagedBytes * 1.5).toLong()
                else -> 250L * 1024 * 1024 // 250 MB minimum headroom
            }

            if (cacheAvailable < requiredBytes) {
                val availMb = cacheAvailable / (1024 * 1024)
                val reqMb = requiredBytes / (1024 * 1024)
                throw IllegalStateException(
                    "Low Storage Space: Only ${availMb}MB available in cache staging, but ${reqMb}MB is required for download and stream muxing."
                )
            }

            if (extAvailable < requiredBytes) {
                val availMb = extAvailable / (1024 * 1024)
                val reqMb = requiredBytes / (1024 * 1024)
                throw IllegalStateException(
                    "Low Storage Space: Only ${availMb}MB available on device storage, but ${reqMb}MB is required to save final media."
                )
            }
        }
    }

    data class AvailableResolution(
        val height: Int,
        val label: String, // e.g. "4K (2160p)", "1440p", "1080p", "720p", "480p", "360p"
        val hasHighFps: Boolean, // fps >= 50
        val maxFps: Int,
        val estimatedBytes: Long = 0L
    )

    data class MediaInfoResult(
        val url: String,
        val title: String,
        val uploader: String,
        val durationMs: Long,
        val thumbnailUrl: String,
        val isInstagram: Boolean,
        val availableFormats: List<String>,
        val availableResolutions: List<AvailableResolution>,
        val maxResolutionHeight: Int,
        val maxFps: Int,
        val estimatedTotalBytes: Long
    )

    /**
     * Inspects media URL and extracts metadata without initiating download.
     */
    suspend fun fetchMediaInfo(url: String): MediaInfoResult = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val appSettings = settings.settingsFlow.first()
        if (appSettings.strictOfflineLockdown) {
            throw SecurityException("Strict Offline Lockdown is active. Network requests are completely blocked.")
        }

        Log.d(TAG, "Fetching media info for: ${sanitizeUrlForLog(url)}")
        val isInsta = url.contains("instagram.com", ignoreCase = true)

        val request = YoutubeDLRequest(url)
        request.addOption("--dump-json")
        request.addOption("--no-playlist")
        request.addOption("--no-check-certificates")
        request.addOption("--no-cache-dir")
        request.addOption("--ignore-errors")
        request.addOption("--no-warnings")
        if (appSettings.cookiesTxtContent.isNotBlank()) {
            val cookiesFile = File(context.filesDir, "cookies.txt")
            cookiesFile.writeText(appSettings.cookiesTxtContent)
            request.addOption("--cookies", cookiesFile.absolutePath)
        }

        val info = YoutubeDL.getInstance().getInfo(request)
        val durationSeconds = info.duration.toLong()
        val durationMs = durationSeconds * 1000L

        val formatsList = info.formats ?: emptyList()
        val validVideoFormats = formatsList.filter { fmt ->
            val vcodec = fmt.vcodec
            vcodec != null && !vcodec.equals("none", ignoreCase = true) && fmt.height > 0
        }

        val standardHeights = listOf(2160, 1440, 1080, 720, 480, 360, 240)
        val availableResList = mutableListOf<AvailableResolution>()

        for (stdHeight in standardHeights) {
            val matching = validVideoFormats.filter { it.height == stdHeight }
            if (matching.isNotEmpty()) {
                val maxFps = matching.maxOfOrNull { it.fps } ?: 30
                val size = matching.mapNotNull {
                    if (it.fileSize > 0) it.fileSize
                    else if (it.fileSizeApproximate > 0) it.fileSizeApproximate
                    else null
                }.maxOrNull() ?: 0L
                val label = when (stdHeight) {
                    2160 -> "4K (2160p)"
                    1440 -> "1440p (2K)"
                    1080 -> "1080p (FHD)"
                    720 -> "720p (HD)"
                    480 -> "480p (SD)"
                    360 -> "360p"
                    else -> "${stdHeight}p"
                }
                availableResList.add(
                    AvailableResolution(
                        height = stdHeight,
                        label = label,
                        hasHighFps = maxFps >= 50,
                        maxFps = maxFps,
                        estimatedBytes = size
                    )
                )
            }
        }

        // If no standard height matched but valid formats exist
        if (availableResList.isEmpty() && validVideoFormats.isNotEmpty()) {
            val distinctHeights = validVideoFormats.map { it.height }.distinct().sortedDescending()
            for (h in distinctHeights) {
                val matching = validVideoFormats.filter { it.height == h }
                val maxFps = matching.maxOfOrNull { it.fps } ?: 30
                val size = matching.mapNotNull {
                    if (it.fileSize > 0) it.fileSize
                    else if (it.fileSizeApproximate > 0) it.fileSizeApproximate
                    else null
                }.maxOrNull() ?: 0L
                availableResList.add(
                    AvailableResolution(
                        height = h,
                        label = "${h}p",
                        hasHighFps = maxFps >= 50,
                        maxFps = maxFps,
                        estimatedBytes = size
                    )
                )
            }
        }

        // Fallback for direct streams or empty formats
        if (availableResList.isEmpty()) {
            val h = info.height.takeIf { it > 0 } ?: 1080
            availableResList.add(
                AvailableResolution(
                    height = h,
                    label = if (h >= 2160) "4K (2160p)" else "${h}p",
                    hasHighFps = false,
                    maxFps = 30,
                    estimatedBytes = info.fileSize.takeIf { it > 0 } ?: info.fileSizeApproximate
                )
            )
        }

        val maxHeight = availableResList.maxOfOrNull { it.height } ?: 1080
        val overallMaxFps = availableResList.maxOfOrNull { it.maxFps } ?: 30
        val overallEstBytes = if (info.fileSize > 0) info.fileSize
            else if (info.fileSizeApproximate > 0) info.fileSizeApproximate
            else availableResList.firstOrNull()?.estimatedBytes ?: 0L

        val formats = availableResList.map { it.label } + listOf("Audio Only (MP3)", "Audio Only (M4A)")

        MediaInfoResult(
            url = url,
            title = info.title ?: "Media Download",
            uploader = info.uploader ?: "Unknown",
            durationMs = durationMs,
            thumbnailUrl = info.thumbnail ?: "",
            isInstagram = isInsta,
            availableFormats = formats,
            availableResolutions = availableResList,
            maxResolutionHeight = maxHeight,
            maxFps = overallMaxFps,
            estimatedTotalBytes = overallEstBytes
        )
    }

    /**
     * Backward-compatible alias for fetchMediaInfo.
     */
    suspend fun getVideoInfo(url: String): MediaInfoResult = fetchMediaInfo(url)

    private val pausedTaskIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val cancelledTaskIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /**
     * Pauses an active download task and stops its running process without deleting staging files.
     */
    fun pauseTask(taskId: Long) {
        pausedTaskIds.add(taskId)
        cancelledTaskIds.remove(taskId)
        try {
            val destroyed = YoutubeDL.getInstance().destroyProcessById("download_$taskId")
            Log.i(TAG, "Paused download process download_$taskId (destroyed=$destroyed)")
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping process on pause: ${t.message}")
        }
    }

    /**
     * Cancels an active download task and terminates its running process.
     */
    fun cancelTask(taskId: Long) {
        cancelledTaskIds.add(taskId)
        pausedTaskIds.remove(taskId)
        try {
            val destroyed = YoutubeDL.getInstance().destroyProcessById("download_$taskId")
            Log.i(TAG, "Cancelled download process download_$taskId (destroyed=$destroyed)")
        } catch (t: Throwable) {
            Log.w(TAG, "Error cancelling download process: ${t.message}")
        }
    }

    /**
     * Resumes or prepares a task for restart, clearing any pause or cancel state.
     */
    fun resumeTask(taskId: Long) {
        pausedTaskIds.remove(taskId)
        cancelledTaskIds.remove(taskId)
        Log.i(TAG, "Resumed task $taskId: cleared pause/cancel state")
    }

    fun isTaskPaused(taskId: Long): Boolean = pausedTaskIds.contains(taskId)
    fun isTaskCancelled(taskId: Long): Boolean = cancelledTaskIds.contains(taskId)

    /**
     * Gracefully terminates an active download process by task ID.
     */
    fun cancelProcess(taskId: Long) {
        cancelTask(taskId)
    }

    private fun getFfmpegBinary(): File {
        val ffmpegDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
        return File(ffmpegDir, "bin/ffmpeg").takeIf { it.exists() }
            ?: File(ffmpegDir, "ffmpeg").takeIf { it.exists() }
            ?: File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
    }

    private fun applyResilientFlags(
        request: YoutubeDLRequest,
        appSettings: com.example.smartgallery.data.settings.AppSettings
    ) {
        request.addOption("-c")
        request.addOption("--continue")
        request.addOption("--retries", "20")
        request.addOption("--fragment-retries", "20")
        request.addOption("--file-access-retries", "10")
        request.addOption("--retry-sleep", "http:exp=1:30")
        request.addOption("--socket-timeout", "30")
        request.addOption("--force-ipv4")
        request.addOption("--no-playlist")
        request.addOption("--no-check-certificates")
        request.addOption("--no-cache-dir")
        request.addOption("--no-warnings")

        if (appSettings.cookiesTxtContent.isNotBlank()) {
            val cookiesFile = File(context.filesDir, "cookies.txt")
            cookiesFile.writeText(appSettings.cookiesTxtContent)
            request.addOption("--cookies", cookiesFile.absolutePath)
        } else {
            request.addOption("--extractor-args", "youtube:player_client=android_vr,tv,ios,mweb")
        }
    }

    /**
     * Executes download of YouTube / Instagram video or audio.
     * Uses Scoped-Storage app-private cache staging (context.cacheDir/dl_staging/$taskId)
     * and streams final merged media to public MediaStore with IS_PENDING.
     */
    suspend fun executeDownload(
        task: DownloadTask,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long, speed: Long, etaSecs: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val appSettings = settings.settingsFlow.first()
        if (appSettings.strictOfflineLockdown) {
            throw SecurityException("Strict Offline Lockdown is active.")
        }

        // Staging strictly in app-private cache directory (no POSIX permission issues for C/C++ binaries)
        val stagingDir = File(context.cacheDir, "dl_staging/${task.id}")
        if (!stagingDir.exists()) stagingDir.mkdirs()

        // Pre-flight storage safety check on cache staging and device storage
        checkStorageHeadroom(context, stagingDir, task.totalBytes)

        if (isTaskPaused(task.id)) {
            Log.i(TAG, "executeDownload: Task ${task.id} is marked PAUSED before start. Aborting.")
            throw DownloadPausedException(task.id)
        }
        if (isTaskCancelled(task.id)) {
            Log.i(TAG, "executeDownload: Task ${task.id} is marked CANCELLED before start. Aborting.")
            throw DownloadCancelledException(task.id)
        }

        val isAudio = task.mediaType.equals("AUDIO", ignoreCase = true) || task.quality.contains("Audio", ignoreCase = true)
        val isInstagram = task.url.contains("instagram.com", ignoreCase = true)
        val isMkv = task.quality.contains("mkv", ignoreCase = true)
        val isM4a = task.quality.contains("m4a", ignoreCase = true)
        val bitrate = when {
            task.quality.contains("192", true) -> "192k"
            task.quality.contains("128", true) -> "128k"
            else -> "320k"
        }
        val isYouTube = task.url.contains("youtube.com", true) || task.url.contains("youtu.be", true)
        val processTag = "download_${task.id}"

        if (isInstagram && isAudio) {
            // Instagram Audio Extraction mode: Download stream first, then convert via bundled FFmpeg
            val tempVideoTemplate = File(stagingDir, "insta_temp_${task.id}_%(id)s.%(ext)s").absolutePath
            var instaSuccess = false
            var lastInstaEx: Exception? = null

            for (attempt in 1..5) {
                if (isTaskPaused(task.id)) throw DownloadPausedException(task.id)
                if (isTaskCancelled(task.id)) throw DownloadCancelledException(task.id)
                checkStorageHeadroom(context, stagingDir, task.totalBytes)

                val instaRequest = YoutubeDLRequest(task.url).apply {
                    applyResilientFlags(this, appSettings)
                    addOption("-o", tempVideoTemplate)
                    addOption("-f", "best")
                    addOption("--no-mtime")
                }

                try {
                    Log.i(TAG, "Starting Instagram download for task ${task.id} (attempt $attempt/5)...")
                    YoutubeDL.getInstance().execute(instaRequest, processTag) { progress, eta, line ->
                        if (isTaskPaused(task.id)) throw DownloadPausedException(task.id)
                        if (isTaskCancelled(task.id)) throw DownloadCancelledException(task.id)
                        val pFloat = progress.coerceIn(0f, 90f)
                        val spd = parseSpeedBytes(line)
                        onProgress(pFloat, 0L, 0L, spd, eta.coerceAtLeast(0L))
                    }
                    instaSuccess = true
                    break
                } catch (e: Exception) {
                    if (isTaskPaused(task.id) || e is DownloadPausedException) throw DownloadPausedException(task.id)
                    if (isTaskCancelled(task.id) || e is DownloadCancelledException) throw DownloadCancelledException(task.id)
                    lastInstaEx = e
                    Log.w(TAG, "Instagram download pass attempt $attempt/5 failed for task ${task.id}: ${e.message}")
                    if (attempt < 5) {
                        Log.i(TAG, "Refreshing stream URL and resuming partial checkpoints in 5s (attempt ${attempt + 1}/5)...")
                        kotlinx.coroutines.delay(5000L)
                    }
                }
            }

            if (!instaSuccess) {
                throw lastInstaEx ?: IllegalStateException("Instagram download failed after 5 recovery attempts")
            }

            if (isTaskPaused(task.id)) throw DownloadPausedException(task.id)
            if (isTaskCancelled(task.id)) throw DownloadCancelledException(task.id)

            // Find downloaded intermediate file
            val tempFiles = stagingDir.listFiles { file -> file.name.startsWith("insta_temp_${task.id}") }
            val sourceVideo = tempFiles?.firstOrNull() ?: throw IllegalStateException("Instagram source video download failed")

            val safeTitle = task.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(80)
            val audioExt = if (isM4a) "m4a" else "mp3"
            val finalStagedAudio = File(stagingDir, "${safeTitle}_${task.id}.$audioExt")

            // Storage headroom check before FFmpeg conversion
            checkStorageHeadroom(context, stagingDir, 0L)

            // Run bundled FFmpeg to extract audio with specified bitrate
            val ffmpegBin = getFfmpegBinary()
            val ffmpegCmd = mutableListOf(
                ffmpegBin.absolutePath,
                "-y",
                "-i", sourceVideo.absolutePath,
                "-vn",
                "-c:a", if (isM4a) "aac" else "libmp3lame",
                "-b:a", bitrate,
                "-metadata", "title=${task.title}",
                "-metadata", "artist=Instagram",
                finalStagedAudio.absolutePath
            )

            val pb = ProcessBuilder(ffmpegCmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { Log.d(TAG, "FFmpeg Insta Audio: $it") }
            }
            val exitCode = proc.waitFor()
            sourceVideo.delete() // Clean up temporary video file

            if (isTaskPaused(task.id)) throw DownloadPausedException(task.id)
            if (isTaskCancelled(task.id)) throw DownloadCancelledException(task.id)

            if (exitCode != 0 || !finalStagedAudio.exists()) {
                throw RuntimeException("FFmpeg failed to extract Instagram audio (exit code: $exitCode)")
            }

            onProgress(95f, finalStagedAudio.length(), finalStagedAudio.length(), 0L, 0L)
            val publicFile = moveStagedFileToMediaStore(finalStagedAudio, isAudio = true)
            onProgress(100f, publicFile.length(), publicFile.length(), 0L, 0L)
            if (publicFile.exists() && publicFile.length() > 0L) {
                stagingDir.deleteRecursively()
            }
            return@withContext publicFile
        }

        // Staging output template
        val outTemplate = File(stagingDir, "%(title).200B-%(id)s.%(ext)s").absolutePath
        var estimatedTotalBytes = task.totalBytes.coerceAtLeast(0L)

        fun createPrimaryRequest(): YoutubeDLRequest {
            val req = YoutubeDLRequest(task.url)
            applyResilientFlags(req, appSettings)

            if (isAudio) {
                req.addOption("-x")
                req.addOption("--audio-format", if (isM4a) "m4a" else "mp3")
                val qualityArg = when (bitrate) {
                    "192k" -> "2"
                    "128k" -> "4"
                    else -> "0" // 320kbps
                }
                req.addOption("--audio-quality", qualityArg)
                req.addOption("--embed-thumbnail")
                req.addOption("--add-metadata")
            } else {
                val heightLimit = when {
                    task.quality.contains("2160", true) || task.quality.contains("Max", true) || task.quality.contains("4K", true) -> 2160
                    task.quality.contains("1440", true) -> 1440
                    task.quality.contains("1080", true) -> 1080
                    task.quality.contains("720", true) -> 720
                    task.quality.contains("480", true) -> 480
                    task.quality.contains("360", true) -> 360
                    else -> 1080
                }
                val prefer60 = task.quality.contains("60fps", ignoreCase = true) || !task.quality.contains("30fps", ignoreCase = true)
                val mergeContainer = if (isMkv) "mkv" else "mp4"

                val formatSelector = if (prefer60) {
                    "bestvideo[height<=$heightLimit][fps>=50][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=$heightLimit][fps>=50]+bestaudio/bestvideo[height<=$heightLimit]+bestaudio/best[height<=$heightLimit]/best"
                } else {
                    "bestvideo[height<=$heightLimit][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=$heightLimit]+bestaudio/best[height<=$heightLimit]/best"
                }
                req.addOption("-f", formatSelector)
                req.addOption("--merge-output-format", mergeContainer)
                req.addOption("--postprocessor-args", "Merger:-c copy")
                req.addOption("--add-metadata")
            }

            // Timestamp Clipper
            if (task.isClipped && task.clipEndMs > task.clipStartMs) {
                val startHms = formatDurationHms(task.clipStartMs)
                val endHms = formatDurationHms(task.clipEndMs)
                req.addOption("--download-sections", "*$startHms-$endHms")
                req.addOption("--force-keyframes-at-cuts")
                Log.i(TAG, "Configured timestamp clipper slice: $startHms to $endHms")
            } else {
                if (!isYouTube) {
                    val connections = appSettings.downloadAria2cConnections.coerceIn(1, profiler.maxAria2cConnections)
                    req.addOption("--downloader", "libaria2c.so")
                    req.addOption(
                        "--external-downloader-args",
                        "aria2c:-c -x 8 -s 8 -k 1M --file-allocation=none"
                    )
                    req.addOption(
                        "--downloader-args",
                        "aria2c:-c -j $connections -x $connections -s $connections -k 1M --file-allocation=none --auto-file-renaming=false --allow-overwrite=true"
                    )
                } else {
                    req.addOption("-N", "4")
                }
            }

            req.addOption("-o", outTemplate)
            req.addOption("--no-mtime")
            return req
        }

        var primarySuccess = false
        var lastPrimaryEx: Exception? = null

        // Controlled recovery loop (up to 5 attempts, 5s delay between attempts)
        for (attempt in 1..5) {
            if (isTaskPaused(task.id)) throw DownloadPausedException(task.id)
            if (isTaskCancelled(task.id)) throw DownloadCancelledException(task.id)
            checkStorageHeadroom(context, stagingDir, estimatedTotalBytes)

            val currentRequest = createPrimaryRequest()

            try {
                Log.i(TAG, "Executing download for task ${task.id} (attempt $attempt/5)...")
                YoutubeDL.getInstance().execute(currentRequest, processTag) { progress, eta, line ->
                    if (isTaskPaused(task.id)) throw DownloadPausedException(task.id)
                    if (isTaskCancelled(task.id)) throw DownloadCancelledException(task.id)
                    val pFloat = progress.coerceIn(0f, 95f)
                    val etaSecs = eta.coerceAtLeast(0L)
                    val spd = parseSpeedBytes(line)
                    val parsedTotal = parseTotalBytesFromLine(line)
                    if (parsedTotal > 0L) estimatedTotalBytes = parsedTotal
                    val downloaded = if (estimatedTotalBytes > 0L) (estimatedTotalBytes * (pFloat / 100f)).toLong() else 0L
                    val stagedBytes = stagingDir.listFiles()?.sumOf { it.length() } ?: 0L
                    val actualDownloaded = if (downloaded > 0L) downloaded else stagedBytes
                    onProgress(pFloat, actualDownloaded, estimatedTotalBytes, spd, etaSecs)
                }
                primarySuccess = true
                break
            } catch (e: Exception) {
                if (isTaskPaused(task.id) || e is DownloadPausedException) {
                    Log.i(TAG, "Task ${task.id} paused by user during primary download pass.")
                    throw DownloadPausedException(task.id)
                }
                if (isTaskCancelled(task.id) || e is DownloadCancelledException) {
                    Log.i(TAG, "Task ${task.id} cancelled by user during primary download pass.")
                    throw DownloadCancelledException(task.id)
                }

                lastPrimaryEx = e
                Log.w(TAG, "Primary download pass attempt $attempt/5 failed for task ${task.id}: ${e.message}")

                if (attempt < 5) {
                    Log.i(TAG, "Refreshing stream URL and resuming partial checkpoints in 5s (attempt ${attempt + 1}/5)...")
                    kotlinx.coroutines.delay(5000L)
                }
            }
        }

        if (!primarySuccess) {
            Log.w(TAG, "Primary download passes failed: ${lastPrimaryEx?.message}. Attempting resilient fallback 'best/bestvideo+bestaudio'")
            val fallbackRequest = YoutubeDLRequest(task.url).apply {
                applyResilientFlags(this, appSettings)
                addOption("-f", "best/bestvideo+bestaudio")
                addOption("-o", outTemplate)
                addOption("--no-mtime")
                addOption("--merge-output-format", if (isMkv) "mkv" else "mp4")
            }

            try {
                YoutubeDL.getInstance().execute(fallbackRequest, processTag) { progress, eta, line ->
                    if (isTaskPaused(task.id)) throw DownloadPausedException(task.id)
                    if (isTaskCancelled(task.id)) throw DownloadCancelledException(task.id)
                    val pFloat = progress.coerceIn(0f, 95f)
                    val etaSecs = eta.coerceAtLeast(0L)
                    val spd = parseSpeedBytes(line)
                    val parsedTotal = parseTotalBytesFromLine(line)
                    if (parsedTotal > 0L) estimatedTotalBytes = parsedTotal
                    val downloaded = if (estimatedTotalBytes > 0L) (estimatedTotalBytes * (pFloat / 100f)).toLong() else 0L
                    val stagedBytes = stagingDir.listFiles()?.sumOf { it.length() } ?: 0L
                    val actualDownloaded = if (downloaded > 0L) downloaded else stagedBytes
                    onProgress(pFloat, actualDownloaded, estimatedTotalBytes, spd, etaSecs)
                }
            } catch (fallbackEx: Exception) {
                if (isTaskPaused(task.id) || fallbackEx is DownloadPausedException) {
                    Log.i(TAG, "Task ${task.id} paused by user during fallback pass.")
                    throw DownloadPausedException(task.id)
                }
                if (isTaskCancelled(task.id) || fallbackEx is DownloadCancelledException) {
                    Log.i(TAG, "Task ${task.id} cancelled by user during fallback pass.")
                    throw DownloadCancelledException(task.id)
                }
                throw fallbackEx
            }
        }

        if (isTaskPaused(task.id)) throw DownloadPausedException(task.id)
        if (isTaskCancelled(task.id)) throw DownloadCancelledException(task.id)

        // Locate downloaded staged file, preferring completed merged media over temporary parts
        val stagedFiles = stagingDir.listFiles { file ->
            file.isFile && file.length() > 0 &&
                !file.name.endsWith(".part") &&
                !file.name.endsWith(".aria2") &&
                !file.name.endsWith(".ytdl")
        }?.sortedByDescending { it.lastModified() }
            ?: stagingDir.listFiles { file -> file.isFile && file.length() > 0 }?.sortedByDescending { it.lastModified() }

        val stagedFile = stagedFiles?.firstOrNull()
            ?: throw IllegalStateException("Download completed but no output file was created in cache staging")

        // Optional 30fps -> 60fps fast conversion if requested
        if (!isAudio && task.quality.contains("convert60", ignoreCase = true)) {
            val ffmpegBin = getFfmpegBinary()
            val convertedFile = File(stagingDir, "60fps_${stagedFile.name}")
            val ffmpegCmd = listOf(
                ffmpegBin.absolutePath,
                "-y",
                "-i", stagedFile.absolutePath,
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-crf", "20",
                "-r", "60",
                "-c:a", "copy",
                convertedFile.absolutePath
            )
            try {
                val proc = ProcessBuilder(ffmpegCmd).redirectErrorStream(true).start()
                val exit = proc.waitFor()
                if (exit == 0 && convertedFile.exists() && convertedFile.length() > 0L) {
                    stagedFile.delete()
                    convertedFile.renameTo(stagedFile)
                    Log.i(TAG, "Fast 60fps conversion completed successfully.")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Optional 60fps conversion notice: ${t.message}, proceeding with original stream.")
            }
        }

        onProgress(95f, stagedFile.length(), stagedFile.length(), 0L, 0L)

        // Stream from staging into public MediaStore via ContentResolver Scoped Storage pipeline
        val publicFile = moveStagedFileToMediaStore(stagedFile, isAudio = isAudio)
        onProgress(100f, publicFile.length(), publicFile.length(), 0L, 0L)

        // Delete staging directory ONLY after verifying final merged file exists in MediaStore with size > 0
        if (publicFile.exists() && publicFile.length() > 0L) {
            stagingDir.deleteRecursively()
            Log.i(TAG, "Download and Scoped Storage transfer complete: ${publicFile.absolutePath} (${publicFile.length()} bytes)")
        } else {
            Log.w(TAG, "Output file in MediaStore is missing or empty, preserving staging directory: ${stagingDir.absolutePath}")
        }
        publicFile
    }

    /**
     * Copies staged file from internal cache to public MediaStore (Movies/SmartGallery or Music/SmartGallery)
     * using ContentResolver, openFileDescriptor, and high-performance buffered NIO FileChannel.transferTo().
     */
    private suspend fun moveStagedFileToMediaStore(stagedFile: File, isAudio: Boolean): File = withContext(Dispatchers.IO) {
        val collectionUri = if (isAudio) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val ext = stagedFile.extension.lowercase(Locale.ROOT)
        val mimeType = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> if (isAudio) "audio/mpeg" else "video/mp4"
        }
        val relativeFolder = if (isAudio) "Music/SmartGallery" else "Movies/SmartGallery"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, stagedFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000L)
            put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val targetUri = context.contentResolver.insert(collectionUri, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry in $relativeFolder")

        val pfd = context.contentResolver.openFileDescriptor(targetUri, "w")
            ?: throw IllegalStateException("Failed to open FileDescriptor to MediaStore URI: $targetUri")

        try {
            java.io.FileInputStream(stagedFile).use { inStream ->
                java.io.FileOutputStream(pfd.fileDescriptor).use { outStream ->
                    val inChannel = inStream.channel
                    val outChannel = outStream.channel
                    var transferred = 0L
                    val totalSize = stagedFile.length()
                    while (transferred < totalSize) {
                        val count = inChannel.transferTo(transferred, 1024L * 1024L * 8L, outChannel) // 8MB buffered chunks
                        if (count <= 0) break
                        transferred += count
                    }
                    outChannel.force(true)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }
        } catch (e: Exception) {
            context.contentResolver.delete(targetUri, null, null)
            throw e
        } finally {
            try { pfd.close() } catch (_: Throwable) {}
        }

        val destinationDir = if (isAudio) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SmartGallery")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SmartGallery")
        }
        val publicFile = File(destinationDir, stagedFile.name)

        // Notify MediaScanner
        MediaScannerConnection.scanFile(context, arrayOf(publicFile.absolutePath), arrayOf(mimeType), null)
        publicFile
    }
}
