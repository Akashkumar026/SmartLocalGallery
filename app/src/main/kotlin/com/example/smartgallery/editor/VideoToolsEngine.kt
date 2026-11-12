package com.example.smartgallery.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
     */
    suspend fun trimVideoLossless(
        inputPath: String,
        startMs: Long,
        endMs: Long
    ): File = withContext(Dispatchers.IO) {
        val inputFile = File(inputPath)
        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SmartGallery/Trimmed")
        if (!outDir.exists()) outDir.mkdirs()

        val outFile = File(outDir, "trim_${System.currentTimeMillis()}_${inputFile.name}")

        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
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

        Log.i(TAG, "Lossless video trimmed successfully to ${outFile.absolutePath}")
        outFile
    }

    /**
     * Extracts MP3/M4A audio from video file using bundled native FFmpeg.
     */
    suspend fun extractAudioFromVideo(
        inputVideoPath: String,
        outputFormat: String = "mp3" // "mp3" or "m4a"
    ): File = withContext(Dispatchers.IO) {
        val inputFile = File(inputVideoPath)
        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SmartGallery/ExtractedAudio")
        if (!outDir.exists()) outDir.mkdirs()

        val outName = "${inputFile.nameWithoutExtension}_audio.${outputFormat}"
        val outFile = File(outDir, outName)

        val cmd = when (outputFormat.lowercase()) {
            "m4a" -> arrayOf("-y", "-i", inputVideoPath, "-vn", "-c:a", "copy", outFile.absolutePath)
            else -> arrayOf("-y", "-i", inputVideoPath, "-vn", "-c:a", "libmp3lame", "-q:a", "2", outFile.absolutePath)
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

        Log.i(TAG, "Audio extracted successfully: ${outFile.absolutePath}")
        outFile
    }
}

