package com.example.smartgallery.downloader

import android.content.Context
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.profiler.DeviceCapabilityProfiler
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DownloaderEngineStateMachineTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: GalleryDatabase
    private lateinit var mockSettings: SettingsRepository
    private lateinit var mockProfiler: DeviceCapabilityProfiler

    class FakeNativeEngineInitializer : NativeEngineInitializer {
        val ytdlInvocations = AtomicInteger(0)
        val ffmpegInvocations = AtomicInteger(0)
        val aria2cInvocations = AtomicInteger(0)

        val shouldFailYtdl = AtomicBoolean(false)
        var initDelayMs: Long = 0L

        override fun initYoutubeDL(context: Context) {
            if (initDelayMs > 0) Thread.sleep(initDelayMs)
            ytdlInvocations.incrementAndGet()
            if (shouldFailYtdl.get()) {
                throw IOException("Native library extraction failed: libpython.zip.so missing")
            }
        }

        override fun initFFmpeg(context: Context) {
            ffmpegInvocations.incrementAndGet()
        }

        override fun initAria2c(context: Context) {
            aria2cInvocations.incrementAndGet()
        }
    }

    private lateinit var fakeInitializer: FakeNativeEngineInitializer
    private lateinit var engine: MediaDownloaderEngine

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockContext = mockk(relaxed = true) {
            every { applicationContext } returns this
        }
        mockDb = mockk(relaxed = true)
        mockSettings = mockk(relaxed = true)
        mockProfiler = mockk(relaxed = true)
        fakeInitializer = FakeNativeEngineInitializer()

        engine = MediaDownloaderEngine(
            context = mockContext,
            db = mockDb,
            settings = mockSettings,
            profiler = mockProfiler,
            nativeInitializer = fakeInitializer
        )
    }

    @Test
    fun testFirstCallInitializesSuccessfully() = runBlocking {
        assertThat(engine.engineState).isInstanceOf(EngineInitState.NotStarted::class.java)
        assertThat(engine.isReady).isFalse()

        val result = engine.ensureReady(mockContext)

        assertThat(result.isSuccess).isTrue()
        assertThat(engine.engineState).isInstanceOf(EngineInitState.Ready::class.java)
        assertThat(engine.isReady).isTrue()
        assertThat(fakeInitializer.ytdlInvocations.get()).isEqualTo(1)
        assertThat(fakeInitializer.ffmpegInvocations.get()).isEqualTo(1)
        assertThat(fakeInitializer.aria2cInvocations.get()).isEqualTo(1)
    }

    @Test
    fun testConcurrentCallsInitializeOnce() = runBlocking {
        fakeInitializer.initDelayMs = 25L

        val jobs = (1..10).map {
            async {
                engine.ensureReady(mockContext)
            }
        }
        val results = jobs.awaitAll()

        results.forEach { res ->
            assertThat(res.isSuccess).isTrue()
        }
        assertThat(engine.isReady).isTrue()
        // Thread-safe mutex must guarantee exactly one invocation across 10 concurrent requests
        assertThat(fakeInitializer.ytdlInvocations.get()).isEqualTo(1)
    }

    @Test
    fun testFailureIsReported() = runBlocking {
        fakeInitializer.shouldFailYtdl.set(true)

        val result = engine.ensureReady(mockContext)

        assertThat(result.isFailure).isTrue()
        assertThat(engine.isReady).isFalse()
        assertThat(engine.engineState).isInstanceOf(EngineInitState.Failed::class.java)

        val failureState = engine.engineState as EngineInitState.Failed
        assertThat(failureState.error.message).contains("libpython.zip.so missing")
    }

    @Test
    fun testFailureIsRetriedOnNextCall_notCachedForever() = runBlocking {
        // First attempt fails
        fakeInitializer.shouldFailYtdl.set(true)
        val firstResult = engine.ensureReady(mockContext)
        assertThat(firstResult.isFailure).isTrue()
        assertThat(engine.isReady).isFalse()
        assertThat(fakeInitializer.ytdlInvocations.get()).isEqualTo(1)

        // Resolve underlying condition
        fakeInitializer.shouldFailYtdl.set(false)

        // Second attempt must retry and succeed, NOT cache previous failure
        val secondResult = engine.ensureReady(mockContext)
        assertThat(secondResult.isSuccess).isTrue()
        assertThat(engine.isReady).isTrue()
        assertThat(engine.engineState).isInstanceOf(EngineInitState.Ready::class.java)
        assertThat(fakeInitializer.ytdlInvocations.get()).isEqualTo(2)
    }

    @Test
    fun testSuccessIsCached() = runBlocking {
        val firstResult = engine.ensureReady(mockContext)
        assertThat(firstResult.isSuccess).isTrue()
        assertThat(fakeInitializer.ytdlInvocations.get()).isEqualTo(1)

        // Subsequent calls should hit cached Ready state immediately
        val secondResult = engine.ensureReady(mockContext)
        val thirdResult = engine.ensureReady(mockContext)

        assertThat(secondResult.isSuccess).isTrue()
        assertThat(thirdResult.isSuccess).isTrue()
        assertThat(fakeInitializer.ytdlInvocations.get()).isEqualTo(1)
    }
}

