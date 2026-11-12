package com.example.smartgallery.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.engine.LocalGalleryEngine
import com.example.smartgallery.profiler.DeviceCapabilityProfiler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class GalleryIndexWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val engine: LocalGalleryEngine,
    private val db: GalleryDatabase,
    private val settings: SettingsRepository,
    private val profiler: DeviceCapabilityProfiler
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "smart_gallery_indexing_channel"
        const val NOTIFICATION_ID = 4001

        fun enqueueOneTime(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<GalleryIndexWorker>().build()
            androidx.work.WorkManager.getInstance(context).enqueue(request)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result = withContext(Dispatchers.Default.limitedParallelism(profiler.aiWorkerParallelism)) {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        } catch (e: Exception) {
            android.util.Log.w("GalleryIndexWorker", "Could not set background thread priority: ${e.message}")
        }

        try {
            // 1. MediaStore Scan
            try {
                android.util.Log.d("GalleryIndexWorker", "Starting MediaStore scan...")
                engine.scanAllMedia()
            } catch (e: Exception) {
                android.util.Log.e("GalleryIndexWorker", "Error scanning media: ${e.message}", e)
            }

            // 2. Hash calculation
            try {
                android.util.Log.d("GalleryIndexWorker", "Computing hashes for unindexed media...")
                engine.computeHashesForUnindexedMedia()
            } catch (e: Exception) {
                android.util.Log.e("GalleryIndexWorker", "Error calculating hashes: ${e.message}", e)
            }

            // 3. Exact duplicates
            try {
                android.util.Log.d("GalleryIndexWorker", "Analyzing exact duplicates...")
                engine.findExactDuplicates()
            } catch (e: Exception) {
                android.util.Log.e("GalleryIndexWorker", "Error finding exact duplicates: ${e.message}", e)
            }

            // 4. Duplicate folders
            try {
                android.util.Log.d("GalleryIndexWorker", "Computing duplicate folders...")
                engine.findDuplicateFolders()
            } catch (e: Exception) {
                android.util.Log.e("GalleryIndexWorker", "Error finding duplicate folders: ${e.message}", e)
            }

            // 5. Laplacian Blur & Burst detection
            try {
                android.util.Log.d("GalleryIndexWorker", "Computing sharpness variance & burst photos...")
                engine.analyzeBurstPhotosAndBlur()
            } catch (e: Exception) {
                android.util.Log.e("GalleryIndexWorker", "Error analyzing blur: ${e.message}", e)
            }

            // 6. Purge old trash (>30 days)
            try {
                val cutoff = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
                db.mediaItemDao().purgeOldTrash(cutoff)
            } catch (e: Exception) {
                android.util.Log.e("GalleryIndexWorker", "Error purging trash: ${e.message}", e)
            }

            // 7. Auto Group by Face Detection
            try {
                android.util.Log.d("GalleryIndexWorker", "Auto grouping faces via MobileFaceNet...")
                val threshold = settings.settingsFlow.first().faceClusterThreshold
                engine.scanFaces(threshold)
            } catch (e: Exception) {
                android.util.Log.e("GalleryIndexWorker", "Error clustering faces: ${e.message}", e)
            }

            // 8. Auto Document & Smart Categorization
            try {
                android.util.Log.d("GalleryIndexWorker", "Auto categorizing smart documents...")
                engine.scanAndGroupDocuments()
            } catch (e: Exception) {
                android.util.Log.e("GalleryIndexWorker", "Error auto-categorizing documents: ${e.message}", e)
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("GalleryIndexWorker", "Fatal error during indexing: ${e.message}", e)
            Result.failure()
        }
    }

    private fun createForegroundInfo(progressText: String): ForegroundInfo {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Indexing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background gallery indexing and duplicate analysis"
            }
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("SmartLocalGallery Indexer")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}

