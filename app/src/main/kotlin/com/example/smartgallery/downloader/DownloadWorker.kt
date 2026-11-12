package com.example.smartgallery.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
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
        downloaderEngine.ensureInitialized(appContext)

        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId == -1L) return@withContext Result.failure()

        val task = db.downloadTaskDao().getByIdSync(taskId) ?: return@withContext Result.failure()

        val notifId = (NOTIFICATION_ID_BASE + (taskId % 1000)).toInt()
        try {
            setForeground(createForegroundInfo(task.title, 0, notifId))
        } catch (t: Throwable) {
            android.util.Log.w("DownloadWorker", "Could not elevate to foreground service: ${t.message}")
        }

        db.downloadTaskDao().updateStatus(taskId, "DOWNLOADING", null, null)

        try {
            val outputFile = downloaderEngine.executeDownload(task) { progress, downloadedBytes, totalBytes, speed, etaSecs ->
                val progressInt = progress.toInt()
                val notification = buildNotification(task.title, progressInt, etaSecs)
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

            // Mark completed in database with valid output path
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
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.message ?: "Unknown error"
            db.downloadTaskDao().updateStatus(taskId, "FAILED", null, errorMsg)
            if (errorMsg.contains("Low Storage Space", ignoreCase = true)) {
                showFailureNotification("Low Storage Space", errorMsg, notifId)
            } else {
                showFailureNotification(task.title, errorMsg, notifId)
            }
            Result.failure()
        }
    }

    private fun createForegroundInfo(title: String, progress: Int, notifId: Int): ForegroundInfo {
        createChannelIfNeeded()
        val notif = buildNotification(title, progress, 0L)
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

    private fun buildNotification(title: String, progress: Int, etaSecs: Long): android.app.Notification {
        val etaText = if (etaSecs > 0) " • ETA: ${etaSecs}s" else ""
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText("$progress%$etaText")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
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

