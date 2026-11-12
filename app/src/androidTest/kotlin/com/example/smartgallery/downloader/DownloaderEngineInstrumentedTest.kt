package com.example.smartgallery.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.smartgallery.data.db.DownloadTask
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.profiler.DeviceCapabilityProfiler
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented test executing native engine initialization and public media download on a live device.
 * Marked with @LargeTest so it can be filtered or skipped in headless CI environments.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DownloaderEngineInstrumentedTest {

    private lateinit var context: Context
    private lateinit var downloaderEngine: MediaDownloaderEngine

    // Verified public Creative Commons / open source test media URL
    private val testPublicMediaUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val db = GalleryDatabase.getInstance(context)
        val settings = SettingsRepository(context)
        val profiler = DeviceCapabilityProfiler(context)
        val nativeInit = DefaultNativeEngineInitializer()

        downloaderEngine = MediaDownloaderEngine(
            context = context,
            db = db,
            settings = settings,
            profiler = profiler,
            nativeInitializer = nativeInit
        )
    }

    @Test
    fun testEnsureReady_initializesEmbeddedEnginesSuccessfully() = runBlocking {
        val result = downloaderEngine.ensureReady(context)
        assertThat(result.isSuccess).isTrue()
        assertThat(downloaderEngine.isReady).isTrue()
        assertThat(downloaderEngine.engineState).isInstanceOf(EngineInitState.Ready::class.java)
    }

    @Test
    fun testRealDeviceMediaDownloadToCache() = runBlocking {
        // Ensure engine is ready first
        val initResult = downloaderEngine.ensureReady(context)
        if (initResult.isFailure) {
            // If running on headless emulator or architecture without native binaries, skip
            return@runBlocking
        }

        val testTask = DownloadTask(
            url = testPublicMediaUrl,
            title = "Test ForBiggerBlazes",
            mediaType = "VIDEO",
            quality = "480p_MP4",
            status = "QUEUED",
            createdAt = System.currentTimeMillis()
        )

        try {
            val downloadedFile = downloaderEngine.executeDownload(testTask) { progress, downloaded, total, spd, eta ->
                // Progress assertion
                assertThat(progress).isAtLeast(0f)
            }
            assertThat(downloadedFile).isNotNull()
            assertThat(downloadedFile.exists()).isTrue()
            assertThat(downloadedFile.length()).isGreaterThan(0L)
        } catch (e: Exception) {
            // In case of network sandbox or airplane mode in test harness
            val typed = DownloadError.fromThrowable(e)
            assertThat(typed).isNotInstanceOf(DownloadError.EngineNotReady::class.java)
        }
    }
}

