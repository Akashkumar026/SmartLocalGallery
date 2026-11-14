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
     * Backward-compatible helper that throws if engine initialization fails.
     */
    suspend fun ensureInitialized(ctx: Context = context) {
        val res = ensureReady(ctx)
        if (res.isFailure) {
            throw res.exceptionOrNull() ?: IllegalStateException("Downloader engine initialization failed")
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
    }

    data class MediaInfoResult(
        val url: String,
        val title: String,
        val uploader: String,
        val durationMs: Long,
        val thumbnailUrl: String,
        val isInstagram: Boolean,
        val availableFormats: List<String>
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

        val formats = listOf("4K (2160p)", "1440p", "1080p", "720p", "480p", "Audio Only (MP3)", "Audio Only (M4A)")

        MediaInfoResult(
            url = url,
            title = info.title ?: "Media Download",
            uploader = info.uploader ?: "Unknown",
            durationMs = durationMs,
            thumbnailUrl = info.thumbnail ?: "",
            isInstagram = isInsta,
            availableFormats = formats
        )
    }

    /**
     * Gracefully terminates an active download process by task ID.
     */
    fun cancelProcess(taskId: Long) {
        try {
            YoutubeDL.getInstance().destroyProcessById("download_$taskId")
            Log.i(TAG, "Successfully requested destruction of download process: download_$taskId")
        } catch (t: Throwable) {
            Log.w(TAG, "Error cancelling download process: ${t.message}")
        }
    }

    private fun getFfmpegBinary(): File {
        val ffmpegDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
        return File(ffmpegDir, "bin/ffmpeg").takeIf { it.exists() }
            ?: File(ffmpegDir, "ffmpeg").takeIf { it.exists() }
            ?: File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
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

        // Pre-flight StatFs check on storage
        val storageDir = Environment.getExternalStorageDirectory()
        val stat = StatFs(storageDir.absolutePath)
        val freeBytes = stat.availableBytes
        val minRequiredBytes = if (task.totalBytes > 0L) (task.totalBytes * 1.25).toLong() else 100L * 1024 * 1024
        if (freeBytes < minRequiredBytes) {
            val freeMb = freeBytes / (1024 * 1024)
            val reqMb = minRequiredBytes / (1024 * 1024)
            throw IllegalStateException("Low Storage Space: Only ${freeMb}MB available, but ${reqMb}MB is required.")
        }

        // Staging strictly in app-private cache directory (no POSIX permission issues for C/C++ binaries)
        val stagingDir = File(context.cacheDir, "dl_staging/${task.id}")
        if (!stagingDir.exists()) stagingDir.mkdirs()

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
        val request = YoutubeDLRequest(task.url)

        // Always pass continue flag to pick up partial downloads
        request.addOption("-c")
        request.addOption("--no-check-certificates")
        request.addOption("--no-cache-dir")
        request.addOption("--ignore-errors")
        request.addOption("--no-warnings")

        // Cookies file handling stored securely in app internal filesDir
        if (appSettings.cookiesTxtContent.isNotBlank()) {
            val cookiesFile = File(context.filesDir, "cookies.txt")
            cookiesFile.writeText(appSettings.cookiesTxtContent)
            request.addOption("--cookies", cookiesFile.absolutePath)
        }

        val processTag = "download_${task.id}"

        if (isInstagram && isAudio) {
            // Instagram Audio Extraction mode: Download stream first, then convert via bundled FFmpeg
            val tempVideoTemplate = File(stagingDir, "insta_temp_${task.id}_%(id)s.%(ext)s").absolutePath
            request.addOption("-o", tempVideoTemplate)
            request.addOption("-f", "best")
            request.addOption("--no-mtime")

            YoutubeDL.getInstance().execute(request, processTag) { progress, eta, line ->
                val pFloat = progress.coerceIn(0f, 90f)
                val spd = parseSpeedBytes(line)
                onProgress(pFloat, 0L, 0L, spd, eta.coerceAtLeast(0L))
            }

            // Find downloaded intermediate file
            val tempFiles = stagingDir.listFiles { file -> file.name.startsWith("insta_temp_${task.id}") }
            val sourceVideo = tempFiles?.firstOrNull() ?: throw IllegalStateException("Instagram source video download failed")

            val safeTitle = task.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(80)
            val audioExt = if (isM4a) "m4a" else "mp3"
            val finalStagedAudio = File(stagingDir, "${safeTitle}_${task.id}.$audioExt")

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

            if (exitCode != 0 || !finalStagedAudio.exists()) {
                throw RuntimeException("FFmpeg failed to extract Instagram audio (exit code: $exitCode)")
            }

            onProgress(95f, finalStagedAudio.length(), finalStagedAudio.length(), 0L, 0L)
            val publicFile = moveStagedFileToMediaStore(finalStagedAudio, isAudio = true)
            onProgress(100f, publicFile.length(), publicFile.length(), 0L, 0L)
            stagingDir.deleteRecursively()
            return@withContext publicFile
        }

        // Format selection & resilient fallback chains
        if (isAudio) {
            request.addOption("-x")
            request.addOption("--audio-format", if (isM4a) "m4a" else "mp3")
            val qualityArg = when (bitrate) {
                "192k" -> "2"
                "128k" -> "4"
                else -> "0" // 320kbps
            }
            request.addOption("--audio-quality", qualityArg)
            request.addOption("--embed-thumbnail")
            request.addOption("--add-metadata")
        } else {
            val heightLimit = when {
                task.quality.contains("2160", true) || task.quality.contains("Max", true) || task.quality.contains("4K", true) -> 2160
                task.quality.contains("1440", true) -> 1440
                task.quality.contains("1080", true) -> 1080
                task.quality.contains("720", true) -> 720
                task.quality.contains("480", true) -> 480
                else -> 1080
            }
            val mergeContainer = if (isMkv) "mkv" else "mp4"
            request.addOption("-f", "bestvideo[height<=$heightLimit][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=$heightLimit]+bestaudio/best[height<=$heightLimit]/best")
            request.addOption("--merge-output-format", mergeContainer)
            request.addOption("--add-metadata")
        }

        // Timestamp Clipper
        if (task.isClipped && task.clipEndMs > task.clipStartMs) {
            val startHms = formatDurationHms(task.clipStartMs)
            val endHms = formatDurationHms(task.clipEndMs)
            request.addOption("--download-sections", "*$startHms-$endHms")
            request.addOption("--force-keyframes-at-cuts")
            Log.i(TAG, "Configured timestamp clipper slice: $startHms to $endHms")
        } else {
            // For non-YouTube direct streams, aria2c acceleration is supported;
            // For YouTube / DASH streams, use yt-dlp's native concurrent fragment downloader (-N 4)
            // to ensure maximum stability without external process exit-code failures.
            if (!isYouTube) {
                val connections = appSettings.downloadAria2cConnections.coerceIn(1, profiler.maxAria2cConnections)
                request.addOption("--downloader", "libaria2c.so")
                request.addOption(
                    "--downloader-args",
                    "aria2c:-c -j $connections -x $connections -s $connections -k 1M --auto-file-renaming=false --allow-overwrite=true"
                )
            } else {
                request.addOption("-N", "4")
            }
        }

        // Staging output template
        val outTemplate = File(stagingDir, "%(title).200B-%(id)s.%(ext)s").absolutePath
        request.addOption("-o", outTemplate)
        request.addOption("--no-mtime")

        var estimatedTotalBytes = 0L

        try {
            YoutubeDL.getInstance().execute(request, processTag) { progress, eta, line ->
                val pFloat = progress.coerceIn(0f, 95f)
                val etaSecs = eta.coerceAtLeast(0L)
                val spd = parseSpeedBytes(line)
                val downloaded = (estimatedTotalBytes * (pFloat / 100f)).toLong()
                onProgress(pFloat, downloaded, estimatedTotalBytes, spd, etaSecs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Initial download pass failed: ${e.message}. Attempting resilient fallback 'best/bestvideo+bestaudio'")
            val fallbackRequest = YoutubeDLRequest(task.url)
            fallbackRequest.addOption("-c")
            fallbackRequest.addOption("--no-check-certificates")
            fallbackRequest.addOption("--no-cache-dir")
            fallbackRequest.addOption("--ignore-errors")
            fallbackRequest.addOption("--no-warnings")
            if (appSettings.cookiesTxtContent.isNotBlank()) {
                val cookiesFile = File(context.filesDir, "cookies.txt")
                fallbackRequest.addOption("--cookies", cookiesFile.absolutePath)
            }
            fallbackRequest.addOption("-f", "best/bestvideo+bestaudio")
            fallbackRequest.addOption("-o", outTemplate)
            fallbackRequest.addOption("--no-mtime")
            fallbackRequest.addOption("--merge-output-format", if (isMkv) "mkv" else "mp4")

            YoutubeDL.getInstance().execute(fallbackRequest, processTag) { progress, eta, line ->
                val pFloat = progress.coerceIn(0f, 95f)
                val etaSecs = eta.coerceAtLeast(0L)
                val spd = parseSpeedBytes(line)
                val downloaded = (estimatedTotalBytes * (pFloat / 100f)).toLong()
                onProgress(pFloat, downloaded, estimatedTotalBytes, spd, etaSecs)
            }
        }

        // Locate downloaded staged file
        val stagedFiles = stagingDir.listFiles { file -> file.isFile && file.length() > 0 }?.sortedByDescending { it.lastModified() }
        val stagedFile = stagedFiles?.firstOrNull()
            ?: throw IllegalStateException("Download completed but no output file was created in cache staging")

        onProgress(95f, stagedFile.length(), stagedFile.length(), 0L, 0L)

        // Stream from staging into public MediaStore via ContentResolver Scoped Storage pipeline
        val publicFile = moveStagedFileToMediaStore(stagedFile, isAudio = isAudio)
        onProgress(100f, publicFile.length(), publicFile.length(), 0L, 0L)

        // Delete staging directory
        stagingDir.deleteRecursively()
        Log.i(TAG, "Download and Scoped Storage transfer complete: ${publicFile.absolutePath} (${publicFile.length()} bytes)")
        publicFile
    }

    /**
     * Copies staged file from internal cache to public MediaStore (Movies/SmartGallery or Music/SmartGallery)
     * using ContentResolver and IS_PENDING.
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

        try {
            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                stagedFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: throw IllegalStateException("Failed to open output stream to MediaStore URI: $targetUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }
        } catch (e: Exception) {
            context.contentResolver.delete(targetUri, null, null)
            throw e
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
