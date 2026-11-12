package com.example.smartgallery.engine

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.exifinterface.media.ExifInterface
import com.example.smartgallery.data.db.*
import com.example.smartgallery.data.settings.SettingsRepository
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.smartgallery.player.ActivePlayerRegistry
import com.example.smartgallery.profiler.DeviceCapabilityProfiler
import com.example.smartgallery.profiler.DeviceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import net.openhft.hashing.LongHashFunction
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class LocalGalleryEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: GalleryDatabase,
    private val settings: SettingsRepository,
    private val profiler: DeviceCapabilityProfiler
) {
    companion object {
        private const val TAG = "LocalGalleryEngine"
        private const val CHUNK_SIZE = 64 * 1024 // 64KB for chunked xxHash64
        private const val FACE_INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 192
    }

    // ─── 1. Incremental MediaStore & SAF Scanner ───────────────────────────

    suspend fun scanAllMedia(): Int = withContext(Dispatchers.IO) {
        var insertedCount = 0
        insertedCount += scanMediaCollection(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "IMAGE"
        )
        insertedCount += scanMediaCollection(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            "VIDEO"
        )
        insertedCount += scanMediaCollection(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "AUDIO"
        )
        Log.i(TAG, "Completed full media scan. Total items processed/inserted: $insertedCount")
        insertedCount
    }

    fun getItemUri(item: MediaItem): Uri {
        return if (item.mediaStoreId > 0L) {
            when (item.mediaType) {
                "IMAGE" -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, item.mediaStoreId)
                "VIDEO" -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.mediaStoreId)
                "AUDIO" -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.mediaStoreId)
                else -> ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), item.mediaStoreId)
            }
        } else {
            Uri.parse(item.absolutePath.let { if (it.startsWith("content://")) it else "file://$it" })
        }
    }

    private suspend fun scanMediaCollection(collectionUri: Uri, mediaType: String): Int {
        var count = 0
        val isVisualOrAudio = mediaType == "VIDEO" || mediaType == "AUDIO"
        val baseProjection = if (isVisualOrAudio) {
            mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.DURATION
            )
        } else {
            mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            baseProjection.add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        }
        val projection = baseProjection.toTypedArray()

        val pageSize = 500
        var offset = 0
        var hasMore = true
        val itemsToInsert = mutableListOf<MediaItem>()

        while (hasMore) {
            try {
                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val bundle = android.os.Bundle().apply {
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, pageSize)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                        putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED))
                        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    }
                    context.contentResolver.query(collectionUri, projection, bundle, null)
                } else {
                    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT $pageSize OFFSET $offset"
                    context.contentResolver.query(collectionUri, projection, null, null, sortOrder)
                }

                if (cursor == null) {
                    hasMore = false
                    break
                }

                cursor.use { c ->
                    val rowCount = c.count
                    if (rowCount == 0) {
                        hasMore = false
                        return@use
                    }

                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val pathCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateAddCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val dateModCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val widthCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                    val bucketIdCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                    val bucketNameCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val durCol = if (isVisualOrAudio) c.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
                    val genModCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        c.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)
                    } else -1

                    while (c.moveToNext()) {
                        try {
                            val msId = c.getLong(idCol)
                            val path = if (pathCol >= 0) c.getStringOrNull(pathCol) ?: "" else ""
                            val dateModified = if (dateModCol >= 0) c.getLong(dateModCol) else 0L
                            val genMod = if (genModCol >= 0) c.getLong(genModCol) else 0L

                            // Skip unchanged files by checking _ID, DATE_MODIFIED, and GENERATION_MODIFIED
                            val existing = db.mediaItemDao().getByMediaStoreId(msId)
                            if (existing != null && existing.dateModified == dateModified &&
                                (genMod == 0L || existing.generationModified == genMod)) {
                                continue
                            }

                            val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                            if (size <= 0L) continue

                            val name = c.getStringOrNull(nameCol) ?: path.substringAfterLast('/', "media_$msId")
                            val mime = if (mimeCol >= 0) c.getStringOrNull(mimeCol) ?: "application/octet-stream" else "application/octet-stream"
                            val dateAdded = if (dateAddCol >= 0) c.getLong(dateAddCol) else dateModified
                            val width = if (widthCol >= 0) c.getInt(widthCol) else 0
                            val height = if (heightCol >= 0) c.getInt(heightCol) else 0
                            val bucketId = if (bucketIdCol >= 0) c.getLong(bucketIdCol) else 0L
                            val bucketName = if (bucketNameCol >= 0) c.getStringOrNull(bucketNameCol) ?: "" else ""

                            val itemUri = ContentUris.withAppendedId(collectionUri, msId)
                            val durationMs = if (durCol >= 0 && !c.isNull(durCol)) {
                                c.getLong(durCol)
                            } else if (mediaType == "VIDEO" || mediaType == "AUDIO") {
                                extractDurationMs(itemUri)
                            } else 0L

                            val smartAlbum = categorizeSmartAlbum(path, name, mime, width, height)
                            val initialCategory = if (path.lowercase().contains("screenshot") || bucketName.lowercase().contains("screenshot") || name.lowercase().contains("screenshot")) {
                                SmartCategoryClassifier.CATEGORY_SCREENSHOTS
                            } else existing?.smartCategory

                            val item = MediaItem(
                                id = existing?.id ?: 0,
                                mediaStoreId = msId,
                                displayName = name,
                                absolutePath = path.ifEmpty { itemUri.toString() },
                                mimeType = mime,
                                size = size,
                                dateAdded = dateAdded,
                                dateModified = dateModified,
                                dateTaken = dateModified * 1000L,
                                durationMs = durationMs,
                                width = width,
                                height = height,
                                bucketId = bucketId,
                                bucketName = bucketName,
                                mediaType = mediaType,
                                albumName = smartAlbum,
                                smartCategory = initialCategory,
                                isUserOverridden = existing?.isUserOverridden ?: false,
                                hasSensitiveId = existing?.hasSensitiveId ?: false,
                                classifierVersion = existing?.classifierVersion ?: 0,
                                generationModified = genMod
                            )

                            itemsToInsert.add(item)
                            count++

                            if (itemsToInsert.size >= 100) {
                                db.mediaItemDao().insertAll(itemsToInsert)
                                itemsToInsert.clear()
                            }
                        } catch (itemErr: Exception) {
                            Log.w(TAG, "Error parsing media item from cursor: ${itemErr.message}")
                        }
                    }

                    offset += rowCount
                    if (rowCount < pageSize) {
                        hasMore = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying collection $collectionUri at offset $offset: ${e.message}")
                hasMore = false
            }
        }

        if (itemsToInsert.isNotEmpty()) {
            itemsToInsert.chunked(100).forEach { batch ->
                db.mediaItemDao().insertAll(batch)
            }
            itemsToInsert.clear()
        }
        return count
    }

    private fun categorizeSmartAlbum(path: String, name: String, mime: String, width: Int, height: Int): String? {
        val lowerPath = path.lowercase()
        return when {
            lowerPath.contains("screenshot") || lowerPath.contains("screen_shot") -> "Screenshots"
            lowerPath.contains("whatsapp") -> "WhatsApp"
            lowerPath.contains("telegram") -> "Telegram"
            lowerPath.contains("instagram") -> "Instagram"
            lowerPath.contains("youtube") || lowerPath.contains("smartgallery/youtube") -> "YouTube Downloads"
            lowerPath.contains("snapchat") -> "Snapchat"
            lowerPath.contains("download") -> "Downloads"
            lowerPath.contains("dcim/camera") -> "Camera"
            else -> null
        }
    }

    private fun extractDurationMs(uri: Uri): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            dur?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun throttleIfPlayerActive() {
        while (ActivePlayerRegistry.isPlayerActive) {
            delay(500)
        }
        val breathingDelay = profiler.aiBreathingDelayMs
        if (breathingDelay > 0L) {
            delay(breathingDelay)
        }
    }

    // ─── 2. Size-First Grouping + xxHash64 Fingerprinting ──────────────────

    fun computeFastFileHash(uri: Uri, fileSize: Long = 0L): String? {
        val hasher = LongHashFunction.xx()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val channel = fis.channel
                    val length = if (fileSize > 0) fileSize else channel.size()
                    if (length <= 0) return null

                    val buffer = ByteBuffer.allocate(CHUNK_SIZE)

                    // 1. First 64KB
                    val firstToRead = min(CHUNK_SIZE.toLong(), length).toInt()
                    buffer.limit(firstToRead)
                    channel.read(buffer)
                    buffer.flip()
                    val firstBytes = ByteArray(buffer.remaining())
                    buffer.get(firstBytes)
                    var runningHash = hasher.hashBytes(firstBytes) xor length

                    // 2. Middle 64KB if file > 128KB
                    if (length > CHUNK_SIZE * 2) {
                        val midOffset = (length / 2) - (CHUNK_SIZE / 2)
                        channel.position(midOffset)
                        buffer.clear()
                        channel.read(buffer)
                        buffer.flip()
                        val midBytes = ByteArray(buffer.remaining())
                        buffer.get(midBytes)
                        val midHash = hasher.hashBytes(midBytes)
                        runningHash = runningHash * 31L + midHash
                    }

                    // 3. Last 64KB if file > 192KB
                    if (length > CHUNK_SIZE * 3) {
                        val lastOffset = length - CHUNK_SIZE
                        channel.position(lastOffset)
                        buffer.clear()
                        channel.read(buffer)
                        buffer.flip()
                        val lastBytes = ByteArray(buffer.remaining())
                        buffer.get(lastBytes)
                        val lastHash = hasher.hashBytes(lastBytes)
                        runningHash = runningHash * 31L + lastHash
                    }

                    java.lang.Long.toHexString(runningHash)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error computing fast hash for $uri: ${t.message}")
            null
        }
    }

    fun computeFastFileHash(path: String): String? {
        val uri = if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
        return computeFastFileHash(uri)
    }

    fun computeXxHash64(path: String): String? = computeFastFileHash(path)

    suspend fun hashItem(item: MediaItem): String? = withContext(Dispatchers.IO) {
        val uri = getItemUri(item)
        val hash = computeFastFileHash(uri, item.size)
        if (hash != null) {
            db.mediaItemDao().updateXxHash(item.id, hash)
        }
        hash
    }

    fun decodeSubsampledBitmap(
        uri: Uri,
        sampleSize: Int = 4,
        preferredConfig: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = preferredConfig
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to decode subsampled bitmap from $uri: ${t.message}")
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun computeHashesForUnindexedMedia() = withContext(Dispatchers.Default.limitedParallelism(profiler.aiWorkerParallelism)) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        val unindexed = db.mediaItemDao().getAllSync().filter { it.xxHash64 == null }
        var count = 0
        val yieldInterval = profiler.aiWorkerYieldInterval
        unindexed.forEach { item ->
            count++
            if (count % yieldInterval == 0) {
                yield()
                throttleIfPlayerActive()
            }
            try {
                val uri = getItemUri(item)
                val hash = computeFastFileHash(uri, item.size)
                if (hash != null) {
                    db.mediaItemDao().updateXxHash(item.id, hash)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping unhashable media item #${item.id} (${item.displayName}): ${e.message}")
            }
        }
    }

    // ─── 3. Duplicate Folders & Duplicate Files Detector ────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun findExactDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.Default.limitedParallelism(profiler.aiWorkerParallelism)) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        val items = db.mediaItemDao().getAllWithXxHash()
        val groups = items.groupBy { it.xxHash64 }
            .filter { it.key != null && it.value.size >= 2 }

        val duplicateGroups = mutableListOf<DuplicateGroup>()
        val now = System.currentTimeMillis()
        var count = 0
        val yieldInterval = profiler.aiWorkerYieldInterval

        for ((_, memberList) in groups) {
            count++
            if (count % yieldInterval == 0) {
                yield()
                throttleIfPlayerActive()
            }
            val memberIds = JSONArray()
            var totalSize = 0L
            var bestKeepItem: MediaItem = memberList.first()

            for (m in memberList) {
                memberIds.put(m.id)
                totalSize += m.size
                if (m.dateAdded > bestKeepItem.dateAdded) {
                    bestKeepItem = m
                }
            }

            duplicateGroups.add(
                DuplicateGroup(
                    groupType = "EXACT",
                    memberIdsJson = memberIds.toString(),
                    totalSizeBytes = totalSize,
                    suggestedKeepId = bestKeepItem.id,
                    detectedAt = now
                )
            )
        }
        db.duplicateGroupDao().insertAll(duplicateGroups)
        duplicateGroups
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun findDuplicateFolders(threshold: Float = 0.80f): List<DuplicateGroup> = withContext(Dispatchers.Default.limitedParallelism(profiler.aiWorkerParallelism)) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        val items = db.mediaItemDao().getAllWithXxHash()
        val folderMap = items.groupBy { it.bucketId }
            .mapValues { entry -> entry.value.mapNotNull { it.xxHash64 }.toSet() }
            .filter { it.value.isNotEmpty() }

        val folderIds = folderMap.keys.toList()
        val duplicateFolderGroups = mutableListOf<DuplicateGroup>()
        val now = System.currentTimeMillis()
        var count = 0
        val yieldInterval = profiler.aiWorkerYieldInterval

        for (i in 0 until folderIds.size) {
            val idA = folderIds[i]
            val setA = folderMap[idA] ?: continue

            for (j in i + 1 until folderIds.size) {
                count++
                if (count % yieldInterval == 0) {
                    yield()
                    throttleIfPlayerActive()
                }
                val idB = folderIds[j]
                val setB = folderMap[idB] ?: continue

                val intersectionSize = setA.intersect(setB).size
                val unionSize = setA.union(setB).size
                if (unionSize == 0) continue

                val jaccard = intersectionSize.toFloat() / unionSize.toFloat()
                if (jaccard >= threshold) {
                    val groupType = if (jaccard >= 0.999f) "EXACT_FOLDER" else "SIMILAR_FOLDER"
                    val memberIds = JSONArray().apply {
                        put(idA)
                        put(idB)
                    }

                    duplicateFolderGroups.add(
                        DuplicateGroup(
                            groupType = groupType,
                            memberIdsJson = memberIds.toString(),
                            totalSizeBytes = 0L,
                            suggestedKeepId = idA,
                            detectedAt = now,
                            jaccardScore = jaccard
                        )
                    )
                }
            }
        }
        db.duplicateGroupDao().insertAll(duplicateFolderGroups)
        duplicateFolderGroups
    }

    // ─── 4. 64-bit dHash & 3x3 Laplacian Blur Detector ─────────────────────

    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = Color.red(scaled.getPixel(x, y))
                val right = Color.red(scaled.getPixel(x + 1, y))
                if (left > right) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        scaled.recycle()
        return hash
    }

    fun dHashDistance(h1: Long, h2: Long): Int = java.lang.Long.bitCount(h1 xor h2)

    /**
     * 3x3 Laplacian Convolution Kernel for Sharpness Variance:
     * [  0,  1,  0 ]
     * [  1, -4,  1 ]
     * [  0,  1,  0 ]
     */
    fun computeLaplacianVariance(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return 0f

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = FloatArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val center = gray[rowOffset + x]
                val top = gray[rowOffset - width + x]
                val bottom = gray[rowOffset + width + x]
                val left = gray[rowOffset + x - 1]
                val right = gray[rowOffset + x + 1]

                // Laplacian 4-neighbor formulation
                val laplacian = top + bottom + left + right - 4f * center

                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return variance.toFloat().coerceAtLeast(0f)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun analyzeBurstPhotosAndBlur(): List<DuplicateGroup> = withContext(Dispatchers.Default.limitedParallelism(profiler.aiWorkerParallelism)) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        val images = db.mediaItemDao().getByType("IMAGE").first()
        val dHashMap = mutableMapOf<Long, Pair<Long, Float>>() // id -> (dHash, laplacianVariance)
        var count = 0
        val yieldInterval = profiler.aiWorkerYieldInterval

        for (item in images) {
            count++
            if (count % yieldInterval == 0) {
                yield()
                throttleIfPlayerActive()
            }
            try {
                val uri = getItemUri(item)
                val bmp = decodeSubsampledBitmap(uri, sampleSize = 4, preferredConfig = Bitmap.Config.RGB_565)
                    ?: continue
                val dhash = computeDHash(bmp)
                val variance = computeLaplacianVariance(bmp)
                bmp.recycle()

                dHashMap[item.id] = Pair(dhash, variance)
                val isBlurry = variance < 100.0f
                db.mediaItemDao().updateBlurMetrics(item.id, variance, false)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping blur analysis for #${item.id}: ${e.message}")
            }
        }

        // Group bursts with Hamming distance <= 4
        val burstGroups = mutableListOf<DuplicateGroup>()
        val visited = mutableSetOf<Long>()
        val itemIds = dHashMap.keys.toList()
        val now = System.currentTimeMillis()
        var pairCheckCount = 0

        for (i in 0 until itemIds.size) {
            val idA = itemIds[i]
            if (visited.contains(idA)) continue

            val groupMembers = mutableListOf<Long>()
            groupMembers.add(idA)
            val (hashA, varA) = dHashMap[idA] ?: continue

            for (j in i + 1 until itemIds.size) {
                pairCheckCount++
                if (pairCheckCount % (yieldInterval * 2) == 0) {
                    yield()
                    throttleIfPlayerActive()
                }
                val idB = itemIds[j]
                if (visited.contains(idB)) continue
                val (hashB, _) = dHashMap[idB] ?: continue

                if (dHashDistance(hashA, hashB) <= 4) {
                    groupMembers.add(idB)
                    visited.add(idB)
                }
            }

            if (groupMembers.size >= 2) {
                visited.add(idA)
                // Pick best shot based on highest laplacian variance
                var bestId = idA
                var maxVar = varA
                for (memberId in groupMembers) {
                    val (_, v) = dHashMap[memberId] ?: continue
                    if (v > maxVar) {
                        maxVar = v
                        bestId = memberId
                    }
                }
                db.mediaItemDao().updateBlurMetrics(bestId, maxVar, true)

                val membersJson = JSONArray()
                groupMembers.forEach { membersJson.put(it) }

                burstGroups.add(
                    DuplicateGroup(
                        groupType = "SIMILAR_IMAGE",
                        memberIdsJson = membersJson.toString(),
                        totalSizeBytes = 0L,
                        suggestedKeepId = bestId,
                        detectedAt = now,
                        dHashDistance = 4,
                        laplacianVariance = maxVar
                    )
                )
            }
        }
        db.duplicateGroupDao().insertAll(burstGroups)
        burstGroups
    }

    // ─── 5. High-Precision Face Clustering & Duplicate-Free People Groups ──

    fun alignFaceAffine(
        sourceBitmap: Bitmap,
        face: Face
    ): Bitmap? {
        val box = face.boundingBox
        if (box.width() < 56 || abs(face.headEulerAngleY) > 32f) {
            return null
        }

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        val angleDeg = if (leftEye != null && rightEye != null) {
            val dy = (rightEye.y - leftEye.y).toDouble()
            val dx = (rightEye.x - leftEye.x).toDouble()
            Math.toDegrees(Math.atan2(dy, dx)).toFloat()
        } else {
            0f
        }

        val centerX = box.exactCenterX()
        val centerY = box.exactCenterY()
        val maxDim = max(box.width(), box.height()).toFloat()
        val halfDim = (maxDim / 2f) * 1.22f // 22% expanded margin

        if (halfDim <= 0f) return null

        val matrix = Matrix()
        matrix.postTranslate(-centerX, -centerY)
        matrix.postRotate(-angleDeg)
        val scale = FACE_INPUT_SIZE / (halfDim * 2f)
        matrix.postScale(scale, scale)
        matrix.postTranslate(FACE_INPUT_SIZE / 2f, FACE_INPUT_SIZE / 2f)

        val aligned = Bitmap.createBitmap(FACE_INPUT_SIZE, FACE_INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(aligned)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, matrix, paint)
        return aligned
    }

    class FaceEmbeddingExtractor(context: Context, tier: DeviceTier = DeviceTier.TIER_MID) {
        private var interpreter: Interpreter? = null
        private var gpuDelegate: GpuDelegate? = null

        init {
            kotlin.runCatching {
                val modelFile = loadModelFile(context, "mobilefacenet.tflite")
                if (modelFile != null) {
                    val options = Interpreter.Options().apply {
                        setNumThreads(if (tier == DeviceTier.TIER_LOW) 1 else 2)
                        setUseNNAPI(tier != DeviceTier.TIER_LOW)
                    }
                    if (tier != DeviceTier.TIER_LOW) {
                        kotlin.runCatching {
                            gpuDelegate = GpuDelegate()
                            options.addDelegate(gpuDelegate)
                        }.onFailure { e ->
                            Log.w(TAG, "GPU delegate unavailable, using CPU/NNAPI: ${e.message}")
                        }
                    }
                    interpreter = Interpreter(modelFile, options)
                    Log.i(TAG, "MobileFaceNet LiteRT model initialized successfully (Tier: $tier).")
                } else {
                    Log.w(TAG, "mobilefacenet.tflite not accessible, face recognition will be bypassed.")
                }
            }.onFailure { t ->
                Log.e(TAG, "Graceful fallback: Failed to load MobileFaceNet LiteRT model: ${t.message}", t)
                interpreter = null
            }
        }

        private fun loadModelFile(context: Context, assetName: String): ByteBuffer? {
            return try {
                context.assets.openFd(assetName).use { fileDescriptor ->
                    FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                        inputStream.channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            fileDescriptor.startOffset,
                            fileDescriptor.declaredLength
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Asset $assetName not accessible or corrupt: ${t.message}")
                null
            }
        }

        fun isModelLoaded(): Boolean = interpreter != null

        fun extract(alignedFace: Bitmap): FloatArray {
            val interp = interpreter ?: return FloatArray(EMBEDDING_SIZE)
            val isFloat = try {
                interp.getInputTensor(0).dataType() == org.tensorflow.lite.DataType.FLOAT32
            } catch (_: Throwable) {
                true
            }

            val byteBuffer = if (isFloat) {
                ByteBuffer.allocateDirect(1 * FACE_INPUT_SIZE * FACE_INPUT_SIZE * 3 * 4)
            } else {
                ByteBuffer.allocateDirect(1 * FACE_INPUT_SIZE * FACE_INPUT_SIZE * 3)
            }
            byteBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(FACE_INPUT_SIZE * FACE_INPUT_SIZE)
            alignedFace.getPixels(intValues, 0, FACE_INPUT_SIZE, 0, 0, FACE_INPUT_SIZE, FACE_INPUT_SIZE)

            for (pixel in intValues) {
                val r = (pixel shr 16 and 0xFF)
                val g = (pixel shr 8 and 0xFF)
                val b = (pixel and 0xFF)
                if (isFloat) {
                    byteBuffer.putFloat((r - 127.5f) / 128.0f)
                    byteBuffer.putFloat((g - 127.5f) / 128.0f)
                    byteBuffer.putFloat((b - 127.5f) / 128.0f)
                } else {
                    byteBuffer.put(r.toByte())
                    byteBuffer.put(g.toByte())
                    byteBuffer.put(b.toByte())
                }
            }

            val outputBuffer = Array(1) { FloatArray(EMBEDDING_SIZE) }
            interp.run(byteBuffer, outputBuffer)

            // L2-normalize 192D output vector: v[i] /= sqrt(sum(v^2))
            val raw = outputBuffer[0]
            var sumSq = 0f
            for (f in raw) sumSq += f * f
            val norm = sqrt(sumSq)
            if (norm > 0f) {
                for (i in raw.indices) raw[i] /= norm
            }
            return raw
        }

        fun close() {
            interpreter?.close()
            gpuDelegate?.close()
        }
    }

    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        var dot = 0f
        val len = min(vecA.size, vecB.size)
        for (i in 0 until len) {
            dot += vecA[i] * vecB[i]
        }
        return dot.coerceIn(-1f, 1f)
    }

    fun normalizeVector(vec: FloatArray) {
        var sumSq = 0f
        for (f in vec) sumSq += f * f
        val norm = sqrt(sumSq)
        if (norm > 0f) {
            for (i in vec.indices) vec[i] /= norm
        }
    }

    fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) buffer.putFloat(f)
        return buffer.array()
    }

    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) floats[i] = buffer.getFloat()
        return floats
    }

    private data class MutableCluster(
        val id: Long,
        var displayName: String,
        var coverMediaId: Long,
        var bestScore: Float,
        var faceCount: Int,
        var centroid: FloatArray
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun scanFaces(threshold: Float = 0.64f, forceRescan: Boolean = false) = withContext(Dispatchers.Default.limitedParallelism(profiler.aiWorkerParallelism)) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        val detectorOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.12f)
            .build()
        val detector = FaceDetection.getClient(detectorOpts)
        val extractor = FaceEmbeddingExtractor(context, profiler.deviceTier)
        if (!extractor.isModelLoaded()) {
            Log.w(TAG, "Face embedding model not loaded; skipping face clustering.")
            detector.close()
            return@withContext
        }

        // Initialize active clusters from database if continuing previous scan
        val activeClusters = mutableListOf<MutableCluster>()
        if (!forceRescan) {
            val existing = db.personClusterDao().getAllSync()
            for (c in existing) {
                val cent = c.centroid?.let { byteArrayToFloatArray(it) } ?: continue
                activeClusters.add(
                    MutableCluster(
                        id = c.id,
                        displayName = c.displayName,
                        coverMediaId = c.coverMediaId,
                        bestScore = 0f,
                        faceCount = c.faceCount,
                        centroid = cent
                    )
                )
            }
        }

        val images = db.mediaItemDao().getByType("IMAGE").first()
        var count = 0
        val yieldInterval = profiler.aiWorkerYieldInterval

        // ─── PASS 1: Face Detection, Embedding & Centroid Assignment ─────────
        for (item in images) {
            count++
            if (count % yieldInterval == 0) {
                yield()
                throttleIfPlayerActive()
            }
            if (!forceRescan && item.faceEmbeddingIds.isNotBlank() && item.faceEmbeddingIds != "[]") {
                continue
            }
            var bmp: Bitmap? = null
            try {
                val uri = getItemUri(item)
                bmp = decodeSubsampledBitmap(uri, sampleSize = 2, preferredConfig = Bitmap.Config.ARGB_8888)
                if (bmp == null) continue

                val inputImage = InputImage.fromBitmap(bmp, 0)
                val faces = detector.process(inputImage).await()
                val embeddingIds = JSONArray()
                val personIds = JSONArray()

                for (face in faces) {
                    if (face.boundingBox.width() < 56 || abs(face.headEulerAngleY) > 32f) continue

                    val aligned = alignFaceAffine(bmp, face) ?: continue
                    val embedding = extractor.extract(aligned)
                    aligned.recycle()

                    val faceScore = (face.boundingBox.width() * face.boundingBox.height()).toFloat() *
                            (1f - abs(face.headEulerAngleY) / 45f).coerceIn(0.1f, 1f)

                    // Find closest matching cluster by cosine similarity
                    var bestSimilarity = -1f
                    var bestCluster: MutableCluster? = null
                    for (c in activeClusters) {
                        val sim = cosineSimilarity(embedding, c.centroid)
                        if (sim > bestSimilarity) {
                            bestSimilarity = sim
                            bestCluster = c
                        }
                    }

                    val clusterId: Long
                    if (bestSimilarity >= threshold && bestCluster != null) {
                        // Assign to existing cluster & update running centroid: normalize(centroid * count + emb)
                        clusterId = bestCluster.id
                        val oldCount = bestCluster.faceCount
                        val newCentroid = FloatArray(EMBEDDING_SIZE)
                        for (i in 0 until EMBEDDING_SIZE) {
                            newCentroid[i] = bestCluster.centroid[i] * oldCount + embedding[i]
                        }
                        normalizeVector(newCentroid)
                        bestCluster.centroid = newCentroid
                        bestCluster.faceCount = oldCount + 1
                        if (faceScore > bestCluster.bestScore) {
                            bestCluster.bestScore = faceScore
                            bestCluster.coverMediaId = item.id
                        }
                        db.personClusterDao().updateClusterStats(
                            clusterId,
                            bestCluster.faceCount,
                            bestCluster.coverMediaId,
                            floatArrayToByteArray(bestCluster.centroid)
                        )
                    } else {
                        // Create brand new person cluster
                        val newCluster = PersonCluster(
                            displayName = "Person #${(System.currentTimeMillis() % 1000).toInt() + count}",
                            coverMediaId = item.id,
                            faceCount = 1,
                            centroid = floatArrayToByteArray(embedding.copyOf()),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        val newId = db.personClusterDao().insert(newCluster)
                        clusterId = newId
                        activeClusters.add(
                            MutableCluster(
                                id = newId,
                                displayName = newCluster.displayName,
                                coverMediaId = item.id,
                                bestScore = faceScore,
                                faceCount = 1,
                                centroid = embedding.copyOf()
                            )
                        )
                    }

                    // Insert into composite primary key cross-reference table (guarantees zero duplicate photos per person)
                    db.personMediaCrossRefDao().insert(
                        PersonMediaCrossRef(
                            clusterId = clusterId,
                            mediaId = item.id,
                            faceScore = faceScore
                        )
                    )

                    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                    val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
                    val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
                    val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position

                    val faceRectJson = JSONObject().apply {
                        put("left", face.boundingBox.left)
                        put("top", face.boundingBox.top)
                        put("right", face.boundingBox.right)
                        put("bottom", face.boundingBox.bottom)
                    }.toString()

                    val faceEntity = FaceEmbedding(
                        mediaItemId = item.id,
                        personId = clusterId,
                        embedding = floatArrayToByteArray(embedding),
                        faceRectJson = faceRectJson,
                        leftEyeX = leftEye?.x ?: 0f,
                        leftEyeY = leftEye?.y ?: 0f,
                        rightEyeX = rightEye?.x ?: 0f,
                        rightEyeY = rightEye?.y ?: 0f,
                        noseTipX = nose?.x ?: 0f,
                        noseTipY = nose?.y ?: 0f,
                        leftMouthX = leftMouth?.x ?: 0f,
                        leftMouthY = leftMouth?.y ?: 0f,
                        rightMouthX = rightMouth?.x ?: 0f,
                        rightMouthY = rightMouth?.y ?: 0f,
                        confidence = face.trackingId?.toFloat() ?: 1.0f,
                        createdAt = System.currentTimeMillis()
                    )
                    val embId = db.faceEmbeddingDao().insert(faceEntity)
                    embeddingIds.put(embId)
                    personIds.put(clusterId)
                }

                if (personIds.length() > 0) {
                    db.mediaItemDao().updateFaceEmbeddingIds(item.id, personIds.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Face detection failed on ${item.displayName}: ${e.message}")
            } finally {
                bmp?.recycle()
            }
        }

        // ─── PASS 2: Agglomerative Cluster Merge (Threshold >= 0.62f) ────────
        val mergeThreshold = 0.62f
        var mergedAny = true
        while (mergedAny && activeClusters.size > 1) {
            mergedAny = false
            var highestSim = -1f
            var pairA: MutableCluster? = null
            var pairB: MutableCluster? = null

            for (i in 0 until activeClusters.size) {
                for (j in i + 1 until activeClusters.size) {
                    val sim = cosineSimilarity(activeClusters[i].centroid, activeClusters[j].centroid)
                    if (sim > highestSim) {
                        highestSim = sim
                        pairA = activeClusters[i]
                        pairB = activeClusters[j]
                    }
                }
            }

            if (highestSim >= mergeThreshold && pairA != null && pairB != null) {
                val countA = pairA.faceCount
                val countB = pairB.faceCount
                val totalCount = countA + countB

                val mergedCentroid = FloatArray(EMBEDDING_SIZE)
                for (k in 0 until EMBEDDING_SIZE) {
                    mergedCentroid[k] = (pairA.centroid[k] * countA + pairB.centroid[k] * countB) / totalCount
                }
                normalizeVector(mergedCentroid)

                val bestCover = if (pairA.bestScore >= pairB.bestScore) pairA.coverMediaId else pairB.coverMediaId
                val bestScore = max(pairA.bestScore, pairB.bestScore)

                pairA.faceCount = totalCount
                pairA.centroid = mergedCentroid
                pairA.coverMediaId = bestCover
                pairA.bestScore = bestScore

                // Atomically re-point cross-references & face embeddings
                db.personMediaCrossRefDao().repointCluster(pairB.id, pairA.id)
                db.faceEmbeddingDao().repointPersonId(pairB.id, pairA.id)
                db.personClusterDao().updateClusterStats(
                    pairA.id,
                    pairA.faceCount,
                    pairA.coverMediaId,
                    floatArrayToByteArray(pairA.centroid)
                )
                db.personClusterDao().deleteById(pairB.id)

                activeClusters.remove(pairB)
                mergedAny = true
                Log.i(TAG, "Agglomerative merge: merged cluster ${pairB.id} into ${pairA.id} (sim=$highestSim)")
            }
        }

        extractor.close()
        detector.close()
    }

    suspend fun mergeClustersManually(clusterA: PersonCluster, clusterB: PersonCluster) = withContext(Dispatchers.IO) {
        val targetId = clusterA.id
        val sourceId = clusterB.id
        if (targetId == sourceId) return@withContext

        db.personMediaCrossRefDao().repointCluster(sourceId, targetId)
        db.faceEmbeddingDao().repointPersonId(sourceId, targetId)

        val centroidA = clusterA.centroid?.let { byteArrayToFloatArray(it) }
        val centroidB = clusterB.centroid?.let { byteArrayToFloatArray(it) }
        val mergedCentroidBytes = if (centroidA != null && centroidB != null) {
            val total = clusterA.faceCount + clusterB.faceCount
            val merged = FloatArray(EMBEDDING_SIZE)
            for (i in 0 until EMBEDDING_SIZE) {
                merged[i] = (centroidA[i] * clusterA.faceCount + centroidB[i] * clusterB.faceCount) / (if (total > 0) total else 1)
            }
            normalizeVector(merged)
            floatArrayToByteArray(merged)
        } else {
            clusterA.centroid ?: clusterB.centroid
        }

        val newCount = clusterA.faceCount + clusterB.faceCount
        val newCover = if (clusterA.coverMediaId != 0L) clusterA.coverMediaId else clusterB.coverMediaId

        db.personClusterDao().updateClusterStats(
            targetId,
            newCount,
            newCover,
            mergedCentroidBytes
        )
        db.personClusterDao().deleteById(sourceId)
    }

    // ─── 6. Live "Select-to-Copy" OCR & Offline Barcode/QR Scanner ─────────

    suspend fun runOcrOnImage(item: MediaItem): String? = withContext(Dispatchers.IO) {
        throttleIfPlayerActive()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val uri = getItemUri(item)
        val bmp = decodeSubsampledBitmap(uri, sampleSize = 1, preferredConfig = Bitmap.Config.ARGB_8888)
            ?: decodeSubsampledBitmap(uri, sampleSize = 2, preferredConfig = Bitmap.Config.RGB_565)
            ?: return@withContext null
        val image = InputImage.fromBitmap(bmp, 0)

        try {
            val result = recognizer.process(image).await()
            val rawText = result.text.trim()
            val (sanitized, hasSensitiveId) = SmartCategoryClassifier.sanitizePii(rawText)
            if (rawText.length >= 20) {
                // Multi-signal intelligent document classification
                val classification = SmartCategoryClassifier.classifyMultiSignal(item, rawText)

                db.mediaItemDao().updateClassification(
                    id = item.id,
                    category = classification.primaryCategory,
                    version = classification.classifierVersion,
                    hasSensitiveId = hasSensitiveId,
                    ocrText = sanitized
                )
                db.mediaFtsDao().insert(
                    id = item.id,
                    name = item.displayName,
                    ocr = sanitized ?: "",
                    bucket = item.bucketName,
                    labels = ""
                )
            }
            sanitized
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed on ${item.displayName}: ${e.message}")
            null
        } finally {
            bmp.recycle()
            recognizer.close()
        }
    }

    suspend fun analyzeImageWithAllSignals(item: MediaItem): String? = withContext(Dispatchers.IO) {
        throttleIfPlayerActive()

        // Fast-path heuristic check
        val quickResult = SmartCategoryClassifier.classifyMultiSignal(item, item.ocrText)
        if (quickResult.primaryCategory == SmartCategoryClassifier.CATEGORY_SCREENSHOTS) {
            db.mediaItemDao().updateClassification(
                id = item.id,
                category = quickResult.primaryCategory,
                version = quickResult.classifierVersion,
                hasSensitiveId = quickResult.hasSensitiveId,
                ocrText = quickResult.sanitizedOcrText
            )
            return@withContext quickResult.primaryCategory
        }

        val uri = getItemUri(item)
        val bmp = decodeSubsampledBitmap(uri, sampleSize = 2, preferredConfig = Bitmap.Config.ARGB_8888)
            ?: decodeSubsampledBitmap(uri, sampleSize = 4, preferredConfig = Bitmap.Config.RGB_565)
            ?: return@withContext quickResult.primaryCategory

        try {
            val inputImage = InputImage.fromBitmap(bmp, 0)

            // 1. Barcode scanner
            val barcodeScanner = BarcodeScanning.getClient()
            val barcodes = try {
                barcodeScanner.process(inputImage).await()
            } catch (_: Exception) {
                emptyList()
            } finally {
                barcodeScanner.close()
            }
            val hasBarcodes = barcodes.isNotEmpty()

            // 2. Face detection
            val faceOpts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
            val faceDetector = FaceDetection.getClient(faceOpts)
            val faces = try {
                faceDetector.process(inputImage).await()
            } catch (_: Exception) {
                emptyList()
            } finally {
                faceDetector.close()
            }
            val faceCount = faces.size
            val totalBmpArea = (bmp.width * bmp.height).toFloat()
            val maxFaceArea = faces.maxOfOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: 0
            val faceAreaFraction = if (totalBmpArea > 0f) maxFaceArea / totalBmpArea else 0f

            // 3. Image labeling
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            val labelsResult = try {
                labeler.process(inputImage).await()
            } catch (_: Exception) {
                emptyList()
            } finally {
                labeler.close()
            }
            val labels = labelsResult.map { Pair(it.text, it.confidence) }

            // 4. OCR if text analysis is required
            var rawOcrText = item.ocrText
            if (rawOcrText.isNullOrBlank() && !hasBarcodes && faceCount == 0) {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                rawOcrText = try {
                    val ocrResult = recognizer.process(inputImage).await()
                    val text = ocrResult.text.trim()
                    text
                } catch (_: Exception) {
                    null
                } finally {
                    recognizer.close()
                }
            }

            // 5. Multi-signal classification & PII Sanitization
            val classification = SmartCategoryClassifier.classifyMultiSignal(
                item = item,
                ocrText = rawOcrText,
                labels = labels,
                faceCount = faceCount,
                faceAreaFraction = faceAreaFraction,
                hasBarcodes = hasBarcodes
            )

            val labelString = labels.joinToString(" ") { it.first }

            db.mediaItemDao().updateClassification(
                id = item.id,
                category = classification.primaryCategory,
                version = classification.classifierVersion,
                hasSensitiveId = classification.hasSensitiveId,
                ocrText = classification.sanitizedOcrText
            )

            db.mediaFtsDao().insert(
                id = item.id,
                name = item.displayName,
                ocr = classification.sanitizedOcrText ?: "",
                bucket = item.bucketName,
                labels = labelString
            )

            classification.primaryCategory
        } catch (e: Exception) {
            Log.w(TAG, "Multi-signal categorization failed for ${item.displayName}: ${e.message}")
            null
        } finally {
            bmp.recycle()
        }
    }

    suspend fun scanAndGroupDocuments(): Int = withContext(Dispatchers.IO) {
        val allImages = db.mediaItemDao().getAllSync().filter { it.mediaType == "IMAGE" }
        var categorizedCount = 0
        for (item in allImages) {
            throttleIfPlayerActive()
            try {
                val cat = analyzeImageWithAllSignals(item)
                if (cat != null) {
                    categorizedCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed categorizing item ${item.displayName}: ${e.message}")
            }
        }
        categorizedCount
    }

    suspend fun scanBarcodes(bitmap: Bitmap): List<String> = withContext(Dispatchers.IO) {
        val scanner = BarcodeScanning.getClient()
        val image = InputImage.fromBitmap(bitmap, 0)
        try {
            val barcodes = scanner.process(image).await()
            barcodes.mapNotNull { it.rawValue }
        } catch (e: Exception) {
            emptyList()
        } finally {
            scanner.close()
        }
    }
}

