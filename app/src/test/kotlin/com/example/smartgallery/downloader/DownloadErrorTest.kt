package com.example.smartgallery.downloader

import com.google.common.truth.Truth.assertThat
import com.yausername.youtubedl_android.YoutubeDLException
import org.junit.Test
import java.io.IOException

class DownloadErrorTest {

    @Test
    fun testParse_instanceNotInitialized_illegalStateException() {
        val ex = IllegalStateException("instance not initialized")
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.EngineNotReady::class.java)
        assertThat(error.isRetryable).isTrue()
        assertThat(error.message).contains("instance not initialized")
    }

    @Test
    fun testParse_instanceNotInitialized_youtubeDLException() {
        val ex = YoutubeDLException("instance not initialized")
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.EngineNotReady::class.java)
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun testParse_failedToInitialize_youtubeDLException() {
        val ex = YoutubeDLException("failed to initialize", IOException("missing libpython.zip.so"))
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.EngineNotReady::class.java)
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun testFromString_engineNotReady() {
        val serialized = "EngineNotReady: Downloader engine not ready"
        val error = DownloadError.fromString(serialized)
        assertThat(error).isInstanceOf(DownloadError.EngineNotReady::class.java)
        assertThat(error.message).isEqualTo("Downloader engine not ready")
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun testParse_storageFull() {
        val ex = IllegalStateException("Low Storage Space: Only 20MB available, but 100MB is required.")
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.StorageFull::class.java)
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun testParse_networkError() {
        val ex = IOException("Unable to download webpage: timed out")
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.Network::class.java)
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun testParse_formatNotFound() {
        val ex = RuntimeException("Requested format is not available for requested container")
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.FormatNotFound::class.java)
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun testParse_unsupportedSite() {
        val ex = IllegalArgumentException("Unsupported URL: ftp://example.com/stream.mp4")
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.UnsupportedSite::class.java)
        assertThat(error.isRetryable).isFalse()
    }

    @Test
    fun testParse_unavailable() {
        val ex = RuntimeException("Video unavailable: Private video or has been removed by uploader")
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.Unavailable::class.java)
        assertThat(error.isRetryable).isFalse()
    }

    @Test
    fun testParse_cancelled() {
        val ex = RuntimeException("Download process download_42 was cancelled by user")
        val error = DownloadError.fromThrowable(ex)
        assertThat(error).isInstanceOf(DownloadError.Cancelled::class.java)
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun testFromString_roundtrip() {
        val serialized = "Network: Socket closed unexpectedly"
        val error = DownloadError.fromString(serialized)
        assertThat(error).isInstanceOf(DownloadError.Network::class.java)
        assertThat(error.message).contains("Socket closed unexpectedly")
    }
}
