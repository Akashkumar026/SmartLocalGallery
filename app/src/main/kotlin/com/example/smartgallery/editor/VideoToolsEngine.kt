package com.example.smartgallery.editor

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoToolsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VideoToolsEngine"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB chunk
    }

    /**
     * Lossless Video Trimming via Android MediaExtractor & MediaMuxer without re-encoding.
     * Compliant with Android 11+ Scoped Storage (stages in cache, then publishes to MediaStore).
     */
    suspend fun trimVideoLossless(
        inputPath: String,
        startMs: Long,
        endMs: Long
    ): File = withContext(Dispatchers.IO) {
        val inputFile = File(inputPath)
        val tempFile = File(context.cacheDir, "temp_trim_${System.currentTimeMillis()}_${inputFile.name}")

        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val trackCount = extractor.trackCount
        val trackMap = HashMap<Int, Int>()

        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                val muxerTrack = muxer.addTrack(format)
                trackMap[i] = muxerTrack
            }
        }

        muxer.start()

        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()

        val startUs = startMs * 1000L
        val endUs = endMs * 1000L

        for ((extTrackIndex, muxerTrackIndex) in trackMap) {
            extractor.selectTrack(extTrackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = extractor.sampleTime
                if (bufferInfo.presentationTimeUs > endUs) break

                if (bufferInfo.presentationTimeUs >= startUs) {
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                }

                extractor.advance()
            }
            extractor.unselectTrack(extTrackIndex)
        }

        extractor.release()
        muxer.stop()
        muxer.release()

        // Publish to MediaStore (Movies/SmartGallery/Trimmed)
        val outDisplayName = "trim_${System.currentTimeMillis()}_${inputFile.name}"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outDisplayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SmartGallery/Trimmed")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val targetUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (targetUri != null) {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(tempFile).use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }
            tempFile.delete()
        }

        Log.i(TAG, "Lossless video trimmed successfully to MediaStore ($outDisplayName)")
        val finalPublicFile = File(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SmartGallery/Trimmed"),
            outDisplayName
        )
        finalPublicFile
    }

    /**
     * Extracts MP3/M4A audio from video file using bundled native FFmpeg.
     * Compliant with Android 11+ Scoped Storage (stages in cache, then publishes to MediaStore).
     */
    suspend fun extractAudioFromVideo(
        inputVideoPath: String,
        outputFormat: String = "mp3" // "mp3" or "m4a"
    ): File = withContext(Dispatchers.IO) {
        val inputFile = File(inputVideoPath)
        val outName = "${inputFile.nameWithoutExtension}_audio.${outputFormat}"
        val tempFile = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}_$outName")

        val cmd = when (outputFormat.lowercase()) {
            "m4a" -> arrayOf("-y", "-i", inputVideoPath, "-vn", "-c:a", "copy", tempFile.absolutePath)
            else -> arrayOf("-y", "-i", inputVideoPath, "-vn", "-c:a", "libmp3lame", "-q:a", "2", tempFile.absolutePath)
        }

        try {
            val ffmpegDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
            val ffmpegBin = File(ffmpegDir, "bin/ffmpeg").takeIf { it.exists() }
                ?: File(ffmpegDir, "ffmpeg").takeIf { it.exists() }
                ?: File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

            val fullCmd = mutableListOf(ffmpegBin.absolutePath).apply { addAll(cmd) }
            val pb = ProcessBuilder(fullCmd)
            pb.redirectErrorStream(true)
            val process = pb.start()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { Log.d(TAG, "FFmpeg: $it") }
            }
            val code = process.waitFor()
            if (code != 0) {
                throw RuntimeException("FFmpeg audio extraction failed with exit code $code")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg extraction failed: ${e.message}")
            throw e
        }

        // Publish to MediaStore (Music/SmartGallery/ExtractedAudio)
        val mime = if (outputFormat.lowercase() == "m4a") "audio/mp4" else "audio/mpeg"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, outName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
            put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SmartGallery/ExtractedAudio")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val targetUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        if (targetUri != null) {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(tempFile).use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }
            tempFile.delete()
        }

        Log.i(TAG, "Audio extracted successfully to MediaStore: $outName")
        val finalPublicFile = File(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SmartGallery/ExtractedAudio"),
            outName
        )
        finalPublicFile
    }
}
