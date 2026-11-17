package com.example.smartgallery.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.smartgallery.data.db.DownloadTask
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.db.MediaItem
import com.example.smartgallery.engine.LocalGalleryEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val downloaderEngine: MediaDownloaderEngine,
    private val db: GalleryDatabase,
    private val galleryEngine: LocalGalleryEngine
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val CHANNEL_ID = "smart_gallery_download_channel"
        const val NOTIFICATION_ID_BASE = 5000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId == -1L) return@withContext Result.failure()

        // Acquire partial wakeLock to prevent OEM battery savers/Doze from killing long multi-hour downloads
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmartGallery:DownloadWakeLock"
        )?.apply {
            setReferenceCounted(false)
            try {
                acquire(1000L * 60 * 60 * 48) // 48-hour safeguard for marathon transfers
                android.util.Log.d("Downloader", "Acquired DownloadWakeLock for task $taskId")
            } catch (t: Throwable) {
                android.util.Log.w("Downloader", "Failed acquiring wakeLock: ${t.message}")
            }
        }

        val notifId = (NOTIFICATION_ID_BASE + (taskId % 1000)).toInt()
        val task = db.downloadTaskDao().getByIdSync(taskId) ?: return@withContext Result.failure()

        try {
            val readyResult = downloaderEngine.ensureReady(appContext)
            if (readyResult.isFailure) {
                val err = readyResult.exceptionOrNull() ?: IllegalStateException("Downloader engine not ready")
                android.util.Log.e("Downloader", "DownloadWorker failed: Engine not ready: ${err.message}", err)
                val typedError = DownloadError.fromThrowable(err)
                val errorMsg = "${typedError::class.simpleName}: ${typedError.message}"
                db.downloadTaskDao().updateStatus(taskId, "FAILED", null, errorMsg)
                showFailureNotification("Downloader Engine Error", typedError.message, notifId)
                return@withContext Result.failure()
            }

            if (downloaderEngine.isTaskPaused(taskId)) {
                android.util.Log.i("Downloader", "DownloadWorker: task $taskId is already marked PAUSED. Exiting cleanly.")
                return@withContext Result.success()
            }

            // Immediately elevate to foreground dataSync service on Android 10+ (API 29+)
            try {
                setForeground(createForegroundInfo(task.title, task.progress.toInt(), notifId, task.speedBytesPerSec))
            } catch (t: Throwable) {
                android.util.Log.w("Downloader", "Could not elevate to foreground service: ${t.message}")
            }

            if (downloaderEngine.isTaskPaused(taskId)) {
                return@withContext Result.success()
            }

            // Pre-flight storage headroom verification before downloading or muxing streams
            val stagingDir = File(appContext.cacheDir, "dl_staging/$taskId")
            try {
                MediaDownloaderEngine.checkStorageHeadroom(appContext, stagingDir, task.totalBytes)
            } catch (storageEx: Throwable) {
                android.util.Log.e("Downloader", "Pre-flight storage check failed for task $taskId: ${storageEx.message}")
                val typedError = DownloadError.fromThrowable(storageEx)
                val errorMsg = "${typedError::class.simpleName}: ${typedError.message}"
                db.downloadTaskDao().updateStatus(taskId, "FAILED", null, errorMsg)
                showFailureNotification("Low Storage Space", typedError.message, notifId)
                return@withContext Result.failure()
            }

            db.downloadTaskDao().updateStatus(taskId, "DOWNLOADING", null, null)

            val outputFile = downloaderEngine.executeDownload(task) { progress, downloadedBytes, totalBytes, speed, etaSecs ->
                if (isStopped || downloaderEngine.isTaskPaused(taskId)) {
                    downloaderEngine.pauseTask(taskId)
                    return@executeDownload
                }
                val progressInt = progress.toInt()
                val notification = buildNotification(task.title, progressInt, etaSecs, speed)
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notifId, notification)

                // Update database progress
                kotlinx.coroutines.runBlocking {
                    db.downloadTaskDao().updateProgress(
                        taskId,
                        progress,
                        downloadedBytes,
                        totalBytes,
                        speed,
                        etaSecs
                    )
                }
            }

            // Mark completed in database with valid output path and updated real media title
            val cleanTitle = outputFile.nameWithoutExtension.replace(Regex("-[a-zA-Z0-9_-]{11}$"), "").ifBlank { outputFile.nameWithoutExtension }
            db.downloadTaskDao().updateTitle(taskId, cleanTitle)
            db.downloadTaskDao().updateCompletion(taskId, "COMPLETED", System.currentTimeMillis(), outputFile.absolutePath, null)

            // Notify MediaScanner and index into Room MediaItem table
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(outputFile.absolutePath),
                null
            ) { path, uri ->
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    val isVideo = path.endsWith(".mp4", true) || path.endsWith(".mkv", true)
                    val mediaType = if (isVideo) "VIDEO" else "AUDIO"
                    val parsedId = if (uri != null) {
                        runCatching { android.content.ContentUris.parseId(uri) }.getOrDefault(System.currentTimeMillis())
                    } else {
                        System.currentTimeMillis()
                    }
                    val item = MediaItem(
                        mediaStoreId = parsedId,
                        displayName = outputFile.name,
                        absolutePath = outputFile.absolutePath,
                        mimeType = if (isVideo) "video/mp4" else "audio/mpeg",
                        size = outputFile.length(),
                        dateAdded = System.currentTimeMillis() / 1000L,
                        dateModified = System.currentTimeMillis() / 1000L,
                        dateTaken = System.currentTimeMillis(),
                        mediaType = mediaType,
                        bucketName = outputFile.parentFile?.name ?: "SmartGallery",
                        albumName = if (isVideo) "SmartGallery Videos" else "SmartGallery Audio"
                    )
                    db.mediaItemDao().insert(item)
                }
            }

            // Show completion notification
            showCompletionNotification(task.title, notifId)
            Result.success()
        } catch (e: Throwable) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val isPaused = e is DownloadPausedException ||
                downloaderEngine.isTaskPaused(taskId) ||
                (isStopped && db.downloadTaskDao().getByIdSync(taskId)?.status == "PAUSED")

            if (isPaused) {
                android.util.Log.i("Downloader", "DownloadWorker: Task $taskId is PAUSED gracefully. Preserving staging checkpoints.")
                db.downloadTaskDao().updateStatus(taskId, "PAUSED", null, null)
                nm.cancel(notifId)
                return@withContext Result.success()
            }

            val isCancelled = e is DownloadCancelledException ||
                downloaderEngine.isTaskCancelled(taskId) ||
                (isStopped && db.downloadTaskDao().getByIdSync(taskId)?.status != "PAUSED")

            if (isCancelled) {
                android.util.Log.i("Downloader", "DownloadWorker: Task $taskId was CANCELLED gracefully.")
                val stagingDir = File(appContext.cacheDir, "dl_staging/$taskId")
                stagingDir.deleteRecursively()
                nm.cancel(notifId)
                return@withContext Result.success()
            }

            android.util.Log.e("Downloader", "DownloadWorker execution failed for task $taskId: ${e.message}", e)
            val typedError = DownloadError.fromThrowable(e)
            val errorMsg = "${typedError::class.simpleName}: ${typedError.message}"
            db.downloadTaskDao().updateStatus(taskId, "FAILED", null, errorMsg)

            // CRITICAL RULE: In FAILED state, dl_staging/$taskId is PRESERVED so retry can resume from checkpoint
            if (typedError is DownloadError.StorageFull) {
                showFailureNotification("Low Storage Space", typedError.message, notifId)
            } else if (typedError is DownloadError.EngineNotReady) {
                showFailureNotification("Downloader Engine Error", typedError.message, notifId)
            } else {
                showFailureNotification(task.title, typedError.message, notifId)
            }
            Result.failure()
        } finally {
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock.release()
                    android.util.Log.d("Downloader", "DownloadWakeLock released successfully for task $taskId")
                } catch (t: Throwable) {
                    android.util.Log.w("Downloader", "Error releasing DownloadWakeLock: ${t.message}")
                }
            }
        }
    }

    private fun createForegroundInfo(title: String, progress: Int, notifId: Int, speed: Long = 0L): ForegroundInfo {
        createChannelIfNeeded()
        val notif = buildNotification(title, progress, 0L, speed)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notifId,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notifId, notif)
        }
    }

    private fun buildNotification(title: String, progress: Int, etaSecs: Long, speed: Long = 0L): android.app.Notification {
        val speedText = if (speed > 0) " • ${MediaDownloaderEngine.formatSpeed(speed)}" else ""
        val etaText = if (etaSecs > 0) " • ETA: ${etaSecs}s" else ""
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText("$progress%$speedText$etaText")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun showCompletionNotification(title: String, notifId: Int) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notif)
    }

    private fun showFailureNotification(title: String, error: String, notifId: Int) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$title: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notif)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground notifications for downloading YouTube and Instagram media"
            }
            nm.createNotificationChannel(channel)
        }
    }
}
