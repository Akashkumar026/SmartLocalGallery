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
        YoutubeDL.getInstance().init(context.applicationContext)
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
