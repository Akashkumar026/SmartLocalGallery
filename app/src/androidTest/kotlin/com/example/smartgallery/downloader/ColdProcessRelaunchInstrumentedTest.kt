package com.example.smartgallery.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.profiler.DeviceCapabilityProfiler
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test simulating cold process launch:
 * Constructs a fresh engine instance simulating process startup from scratch,
 * verifies that ensureReady() properly transitions state and initializes YoutubeDL.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class ColdProcessRelaunchInstrumentedTest {

    @Test
    fun testColdProcessStartup_firstDownloadEnsuresReady() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = GalleryDatabase.getInstance(context)
        val settings = SettingsRepository(context)
        val profiler = DeviceCapabilityProfiler(context)
        val nativeInit = DefaultNativeEngineInitializer()

        // Fresh engine instance simulates cold process start
        val coldEngine = MediaDownloaderEngine(
            context = context,
            db = db,
            settings = settings,
            profiler = profiler,
            nativeInitializer = nativeInit
        )

        // Must start in NotStarted
        assertThat(coldEngine.engineState).isInstanceOf(EngineInitState.NotStarted::class.java)
        assertThat(coldEngine.isReady).isFalse()

        // First download or inspect calls ensureReady()
        val result = coldEngine.ensureReady(context)
        assertThat(result.isSuccess).isTrue()
        assertThat(coldEngine.isReady).isTrue()
        assertThat(coldEngine.engineState).isInstanceOf(EngineInitState.Ready::class.java)
    }
}

