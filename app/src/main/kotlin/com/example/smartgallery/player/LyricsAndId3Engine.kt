package com.example.smartgallery.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class Id3Metadata(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
    val embeddedLyrics: String?,
    val albumArt: Bitmap?
)

@Singleton
class LyricsAndId3Engine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LyricsAndId3Engine"
        // Matches [mm:ss.xx] or [hh:mm:ss.xx]
        private val LRC_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        private val LRC_HOUR_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
    }

    /**
     * Parses .lrc synchronized lyrics file content into timestamped LyricLine list.
     */
    fun parseLrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        content.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            val hourMatcher = LRC_HOUR_PATTERN.matcher(trimmed)
            if (hourMatcher.matches()) {
                val h = hourMatcher.group(1)?.toLongOrNull() ?: 0L
                val m = hourMatcher.group(2)?.toLongOrNull() ?: 0L
                val s = hourMatcher.group(3)?.toLongOrNull() ?: 0L
                val msPart = hourMatcher.group(4) ?: "0"
                val ms = if (msPart.length == 2) msPart.toLong() * 10L else msPart.toLong()
                val text = hourMatcher.group(5)?.trim() ?: ""
                val totalMs = (h * 3600L + m * 60L + s) * 1000L + ms
                lines.add(LyricLine(totalMs, text))
            } else {
                val matcher = LRC_PATTERN.matcher(trimmed)
                if (matcher.matches()) {
                    val m = matcher.group(1)?.toLongOrNull() ?: 0L
                    val s = matcher.group(2)?.toLongOrNull() ?: 0L
                    val msPart = matcher.group(3) ?: "0"
                    val ms = if (msPart.length == 2) msPart.toLong() * 10L else msPart.toLong()
                    val text = matcher.group(4)?.trim() ?: ""
                    val totalMs = (m * 60L + s) * 1000L + ms
                    lines.add(LyricLine(totalMs, text))
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    fun getCurrentLyricIndex(lyrics: List<LyricLine>, currentPositionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var low = 0
        var high = lyrics.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lyrics[mid].timestampMs <= currentPositionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Auto-discovers a matching .lrc file in the same directory as the media file.
     */
    fun findMatchingLrcFile(mediaPath: String): File? {
        val mediaFile = File(mediaPath)
        val nameWithoutExt = mediaFile.nameWithoutExtension
        val parent = mediaFile.parentFile ?: return null

        val directLrc = File(parent, "$nameWithoutExt.lrc")
        if (directLrc.exists() && directLrc.canRead()) return directLrc

        val lyricsSubdir = File(parent, "Lyrics")
        if (lyricsSubdir.exists() && lyricsSubdir.isDirectory) {
            val subLrc = File(lyricsSubdir, "$nameWithoutExt.lrc")
            if (subLrc.exists() && subLrc.canRead()) return subLrc
        }
        return null
    }

    /**
     * Reads ID3 metadata and album art from an audio file.
     */
    suspend fun readId3Metadata(audioPath: String): Id3Metadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(audioPath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: File(audioPath).nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "Music"
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: ""
            val rawArt = retriever.embeddedPicture
            val albumArt = if (rawArt != null) BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size) else null

            Id3Metadata(
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                year = year,
                embeddedLyrics = null,
                albumArt = albumArt
            )
        } catch (e: Exception) {
            Id3Metadata(File(audioPath).nameWithoutExtension, "Unknown Artist", "Unknown Album", "Music", "", null, null)
        } finally {
            retriever.release()
        }
    }

    /**
     * Writes updated ID3 metadata into audio file using bundled FFmpeg.
     */
    suspend fun updateId3Metadata(
        audioPath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: String,
        newGenre: String = "",
        artworkFile: File? = null
    ): File = withContext(Dispatchers.IO) {
        val original = File(audioPath)
        val tempOut = File(original.parentFile, "tagged_${System.currentTimeMillis()}_${original.name}")

        val cmdList = mutableListOf("-y", "-i", original.absolutePath)
        if (artworkFile != null && artworkFile.exists()) {
            cmdList.addAll(listOf("-i", artworkFile.absolutePath, "-map", "0:a", "-map", "1:0", "-c", "copy", "-id3v2_version", "3", "-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"))
        } else {
            cmdList.addAll(listOf("-codec", "copy", "-id3v2_version", "3"))
        }

        cmdList.addAll(listOf(
            "-metadata", "title=$newTitle",
            "-metadata", "artist=$newArtist",
            "-metadata", "album=$newAlbum",
            "-metadata", "date=$newYear"
        ))
        if (newGenre.isNotBlank()) {
            cmdList.addAll(listOf("-metadata", "genre=$newGenre"))
        }
        cmdList.add(tempOut.absolutePath)

        try {
            val ffmpegDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
            val ffmpegBin = File(ffmpegDir, "bin/ffmpeg").takeIf { it.exists() }
                ?: File(ffmpegDir, "ffmpeg").takeIf { it.exists() }
                ?: File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

            val fullCmd = mutableListOf(ffmpegBin.absolutePath).apply { addAll(cmdList) }
            val pb = ProcessBuilder(fullCmd)
            pb.redirectErrorStream(true)
            val process = pb.start()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { Log.d(TAG, "FFmpeg ID3: $it") }
            }
            val code = process.waitFor()
            if (code != 0) {
                throw RuntimeException("FFmpeg ID3 update failed with exit code $code")
            }
            if (tempOut.exists() && tempOut.length() > 0) {
                original.delete()
                tempOut.renameTo(original)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update ID3 tags: ${e.message}")
            throw e
        }
        original
    }
}

