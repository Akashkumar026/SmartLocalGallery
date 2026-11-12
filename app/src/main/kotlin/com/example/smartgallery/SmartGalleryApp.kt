package com.example.smartgallery

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.database.CursorWindow
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.smartgallery.player.ActivePlayerRegistry
import com.example.smartgallery.profiler.DeviceCapabilityProfiler
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import com.yausername.aria2c.Aria2c
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import okio.Path.Companion.toOkioPath
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class SmartGalleryApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var profiler: DeviceCapabilityProfiler

    @Inject
    lateinit var downloaderEngine: dagger.Lazy<com.example.smartgallery.downloader.MediaDownloaderEngine>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashHandler()
        expandCursorWindowSize()
        try {
            WorkManager.initialize(this, workManagerConfiguration)
            Log.d(TAG, "WorkManager initialized successfully in onCreate.")
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager initialization check: ${e.message}")
        }
        initEmbeddedDownloaderBinaries()
    }

    private fun expandCursorWindowSize() {
        try {
            val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 32 * 1024 * 1024) // 32 MB Cursor Window
            Log.i(TAG, "CursorWindow size expanded to 32MB successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Notice: CursorWindow sCursorWindowSize reflection fallback: ${e.message}")
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val memoryPercent = if (::profiler.isInitialized) profiler.memoryCachePercent else 0.18
        val diskMaxBytes = if (::profiler.isInitialized) profiler.diskCacheMaxBytes else 200L * 1024 * 1024
        val crossfadeMs = if (::profiler.isInitialized) profiler.crossfadeDurationMs else 100

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, memoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(diskMaxBytes)
                    .build()
            }
            .components {
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(crossfadeMs)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            try {
                SingletonImageLoader.get(this).memoryCache?.let { memCache ->
                    try {
                        memCache.trimToSize(memCache.size / 2)
                    } catch (_: Throwable) {
                        memCache.clear()
                    }
                    Log.d(TAG, "Trimmed Coil memory cache due to OS memory pressure (level $level)")
                }
                // Safe hint to garbage collector without interrupting active video or active download
                if (!ActivePlayerRegistry.isPlayerActive) {
                    System.gc()
                }
            } catch (e: Exception) {
                Log.w(TAG, "onTrimMemory handler: ${e.message}")
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            SingletonImageLoader.get(this).memoryCache?.clear()
            Log.w(TAG, "Cleared Coil memory cache completely onLowMemory")
        } catch (e: Exception) {
            Log.w(TAG, "onLowMemory handler: ${e.message}")
        }
    }

    override val workManagerConfiguration: Configuration
        get() {
            val builder = Configuration.Builder().setMinimumLoggingLevel(Log.INFO)
            if (::workerFactory.isInitialized) {
                builder.setWorkerFactory(workerFactory)
            } else {
                Log.w(TAG, "HiltWorkerFactory not yet injected; using default WorkerFactory for WorkManager.")
            }
            return builder.build()
        }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashDir = getExternalFilesDir(null) ?: filesDir
                val crashFile = java.io.File(crashDir, "last_crash.txt")
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                pw.flush()
                val report = buildString {
                    append("=== SMARTLOCALGALLERY CRASH REPORT ===\n")
                    append("Timestamp: ${java.util.Date()}\n")
                    append("Thread: ${thread.name} (id: ${thread.id})\n")
                    append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})\n")
                    append("Exception: ${throwable.javaClass.name}: ${throwable.message}\n")
                    append("Stacktrace:\n")
                    append(sw.toString())
                }
                crashFile.writeText(report)
                Log.e(TAG, "FATAL CRASH recorded to ${crashFile.absolutePath}:\n$report")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed writing crash log to disk: ${t.message}")
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun initEmbeddedDownloaderBinaries() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                downloaderEngine.get().ensureReady(applicationContext).fold(
                    onSuccess = {
                        Log.i("Downloader", "Embedded downloader engines initialized successfully via MediaDownloaderEngine.")
                    },
                    onFailure = { t ->
                        Log.w("Downloader", "Background pre-warm of downloader engines notice: ${t.message}", t)
                    }
                )
            } catch (t: Throwable) {
                Log.w("Downloader", "Background pre-warm exception: ${t.message}", t)
            }
        }
    }

    companion object {
        private const val TAG = "SmartGalleryApp"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context
}

