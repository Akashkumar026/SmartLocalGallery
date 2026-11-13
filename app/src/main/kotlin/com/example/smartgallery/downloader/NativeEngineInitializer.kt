package com.example.smartgallery.downloader

import android.content.Context
import androidx.annotation.Keep
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import com.yausername.aria2c.Aria2c
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hard reference to prevent R8 / AAPT2 resource stripping of bundled yt-dlp binary.
 */
@Keep
object DownloaderResourceKeeper {
    @Keep
    val ytdlpRawRes: Int = com.yausername.youtubedl_android.R.raw.ytdlp
}

/**
 * Interface abstracting native downloader engine initialization (YoutubeDL, FFmpeg, Aria2c)
 * allowing deterministic unit testing without native shared libraries.
 */
interface NativeEngineInitializer {
    @Throws(Exception::class)
    fun initYoutubeDL(context: Context)

    @Throws(Exception::class)
    fun initFFmpeg(context: Context)

    @Throws(Exception::class)
    fun initAria2c(context: Context)
}

@Singleton
class DefaultNativeEngineInitializer @Inject constructor() : NativeEngineInitializer {
    override fun initYoutubeDL(context: Context) {
        val appCtx = context.applicationContext
        YoutubeDL.getInstance().init(appCtx)
        syncBundledYtdlBinary(appCtx)
    }

    private fun syncBundledYtdlBinary(appCtx: Context) {
        try {
            val ytdlpDir = java.io.File(appCtx.noBackupFilesDir, "youtubedl-android/yt-dlp")
            val targetFile = java.io.File(ytdlpDir, "yt-dlp")
            val resId = appCtx.resources.getIdentifier("ytdlp", "raw", appCtx.packageName)
            val finalResId = if (resId != 0) resId else com.yausername.youtubedl_android.R.raw.ytdlp
            val resStream = appCtx.resources.openRawResource(finalResId)
            val resBytes = resStream.use { it.readBytes() }
            if (!targetFile.exists() || targetFile.length() != resBytes.size.toLong()) {
                if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
                targetFile.writeBytes(resBytes)
                targetFile.setExecutable(true, false)
                android.util.Log.i("Downloader", "Synchronized bundled yt-dlp binary (${resBytes.size} bytes) to ${targetFile.absolutePath}")
            }
        } catch (t: Throwable) {
            android.util.Log.w("Downloader", "Non-fatal: could not sync bundled yt-dlp: ${t.message}")
        }
    }

    override fun initFFmpeg(context: Context) {
        FFmpeg.getInstance().init(context.applicationContext)
    }

    override fun initAria2c(context: Context) {
        Aria2c.getInstance().init(context.applicationContext)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloaderModule {
    @Binds
    @Singleton
    abstract fun bindNativeEngineInitializer(
        impl: DefaultNativeEngineInitializer
    ): NativeEngineInitializer
}
