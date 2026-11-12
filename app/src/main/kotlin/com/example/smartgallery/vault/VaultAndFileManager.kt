package com.example.smartgallery.vault

import android.app.RecoverableSecurityException
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.db.MediaItem
import com.example.smartgallery.data.db.VaultItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultAndFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: GalleryDatabase
) {
    companion object {
        private const val TAG = "VaultAndFileManager"
        private const val VAULT_DIR_NAME = "private_vault"
    }

    private val vaultDir: File by lazy {
        val dir = File(context.filesDir, VAULT_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    val masterKey: MasterKey by lazy {
        try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        } catch (_: Exception) {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
    }

    fun getEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private fun computeSha256(stream: java.io.InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    data class VaultStagedEncryption(
        val mediaItem: MediaItem,
        val uuid: String,
        val vaultFile: File,
        val thumbFile: File?
    )

    // ─── 1. Zero-Leak AES-256 Vault Encryption & Restoration ────────────────

    suspend fun stageVaultEncryption(mediaItem: MediaItem): VaultStagedEncryption = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val vaultFile = File(vaultDir, "$uuid.vault")

        val encryptedVault = getEncryptedFile(vaultFile)

        val inputStream = try {
            context.contentResolver.openInputStream(mediaItem.contentUri)
        } catch (_: Exception) {
            val f = File(mediaItem.absolutePath)
            if (f.exists()) FileInputStream(f) else null
        } ?: throw IllegalStateException("Cannot open input stream for ${mediaItem.displayName}")

        // 1. Stream source into encrypted file and compute source SHA-256
        val sourceDigest = java.security.MessageDigest.getInstance("SHA-256")
        inputStream.use { input ->
            encryptedVault.openFileOutput().use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sourceDigest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        val sourceHash = sourceDigest.digest().joinToString("") { "%02x".format(it) }

        if (!vaultFile.exists() || vaultFile.length() <= 0L) {
            throw IllegalStateException("Encrypted vault write failed: empty or missing file")
        }

        // 2. Pre-delete Verification: Stream back decrypted bytes and verify SHA-256 matches source
        val decryptedHash = encryptedVault.openFileInput().use { verifyStream ->
            computeSha256(verifyStream)
        }

        if (sourceHash != decryptedHash) {
            vaultFile.delete()
            throw SecurityException("Vault integrity verification failed: Decrypted digest mismatch. Aborting public file deletion to prevent data loss.")
        }

        // 3. Generate encrypted thumbnail for instant memory-safe grid preview
        var thumbFile: File? = null
        try {
            val thumbBitmap = generateThumbnailBitmap(mediaItem)
            if (thumbBitmap != null) {
                val tFile = File(vaultDir, "$uuid.thumb")
                val encThumb = getEncryptedFile(tFile)

                encThumb.openFileOutput().use { outStream ->
                    thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
                }
                thumbBitmap.recycle()
                if (tFile.exists() && tFile.length() > 0L) {
                    thumbFile = tFile
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Thumbnail generation skipped for vault item: ${t.message}")
        }

        VaultStagedEncryption(
            mediaItem = mediaItem,
            uuid = uuid,
            vaultFile = vaultFile,
            thumbFile = thumbFile
        )
    }

    /**
     * Builds a MediaStore delete request IntentSender for user authorization.
     */
    fun createDeleteIntentSender(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } else {
            null
        }
    }

    /**
     * Completes moving item into vault: deletes public database record and saves VaultItemEntity.
     */
    suspend fun finalizeVaultMove(staged: VaultStagedEncryption) = withContext(Dispatchers.IO) {
        val item = staged.mediaItem
        val entity = VaultItemEntity(
            uuid = staged.uuid,
            vaultFilePath = staged.vaultFile.absolutePath,
            thumbnailFilePath = staged.thumbFile?.absolutePath,
            originalMediaStoreId = item.mediaStoreId,
            originalDisplayName = item.displayName,
            originalMimeType = item.mimeType,
            originalSize = item.size,
            originalRelativePath = if (item.mediaType == "AUDIO") "Music" else "Pictures",
            originalBucketName = item.bucketName,
            originalMediaType = item.mediaType,
            originalDurationMs = item.durationMs,
            width = item.width,
            height = item.height,
            encryptedAt = System.currentTimeMillis()
        )
        db.vaultItemDao().insert(entity)
        db.mediaItemDao().deleteById(item.id)
        Log.i(TAG, "Finalized vault migration for #${item.id} (${staged.vaultFile.name})")
    }

    /**
     * Streams decrypted bytes directly into memory. Zero unencrypted temporary disk writes.
     */
    suspend fun loadDecryptedBytes(vaultItem: VaultItemEntity, isThumb: Boolean = false): ByteArray? = withContext(Dispatchers.IO) {
        val targetFile = if (isThumb && !vaultItem.thumbnailFilePath.isNullOrBlank()) {
            File(vaultItem.thumbnailFilePath)
        } else {
            File(vaultItem.vaultFilePath)
        }

        if (!targetFile.exists()) return@withContext null

        // Cap in-memory allocation at 30MB to avoid OOM
        if (!isThumb && targetFile.length() > 30 * 1024 * 1024L) {
            Log.w(TAG, "File too large for heap byte allocation (${targetFile.length()} bytes). Use loadDecryptedBitmap or streaming.")
            return@withContext null
        }

        try {
            val encFile = getEncryptedFile(targetFile)
            encFile.openFileInput().use { input ->
                input.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error on ${targetFile.name}: ${e.message}")
            null
        }
    }

    /**
     * Safely decodes decrypted image bitmap with inSampleSize to prevent OOM.
     */
    suspend fun loadDecryptedBitmap(
        vaultItem: VaultItemEntity,
        reqWidth: Int = 1920,
        reqHeight: Int = 1080,
        isThumb: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val targetFile = if (isThumb && !vaultItem.thumbnailFilePath.isNullOrBlank()) {
            File(vaultItem.thumbnailFilePath)
        } else {
            File(vaultItem.vaultFilePath)
        }
        if (!targetFile.exists()) return@withContext null

        try {
            val encFile = getEncryptedFile(targetFile)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            encFile.openFileInput().use { inStream ->
                BitmapFactory.decodeStream(inStream, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val sampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            encFile.openFileInput().use { inStream ->
                BitmapFactory.decodeStream(inStream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding decrypted bitmap: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Provides an unencrypted stream for audio/video playback or export without saving to disk.
     */
    fun openDecryptedInputStream(vaultItem: VaultItemEntity, isThumb: Boolean = false): java.io.InputStream? {
        val targetFile = if (isThumb && !vaultItem.thumbnailFilePath.isNullOrBlank()) {
            File(vaultItem.thumbnailFilePath)
        } else {
            File(vaultItem.vaultFilePath)
        }
        if (!targetFile.exists()) return null
        return try {
            getEncryptedFile(targetFile).openFileInput()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ExoPlayer DataSource factory that streams encrypted vault media directly from storage.
     */
    @androidx.media3.common.util.UnstableApi
    fun getEncryptedDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        return EncryptedFileDataSourceFactory(context, masterKey) { uri ->
            if (uri.scheme == "vault") {
                val uuid = uri.host ?: uri.path?.removePrefix("/") ?: ""
                File(vaultDir, "$uuid.vault")
            } else {
                uri.path?.let { File(it) }
            }
        }
    }

    /**
     * Restores encrypted vault item back into public MediaStore and unlinks vault ciphertext.
     */
    suspend fun restoreVaultItemToGallery(vaultItem: VaultItemEntity): Boolean = withContext(Dispatchers.IO) {
        val vaultFile = File(vaultItem.vaultFilePath)
        if (!vaultFile.exists()) return@withContext false

        val isAudio = vaultItem.originalMediaType.equals("AUDIO", ignoreCase = true)
        val isVideo = vaultItem.originalMediaType.equals("VIDEO", ignoreCase = true)
        val collectionUri = when {
            isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val relativeFolder = when {
            isAudio -> "Music/SmartGallery"
            isVideo -> "Movies/SmartGallery"
            else -> "Pictures/SmartGallery"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, vaultItem.originalDisplayName)
            put(MediaStore.MediaColumns.MIME_TYPE, vaultItem.originalMimeType)
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000L)
            put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val targetUri = context.contentResolver.insert(collectionUri, values) ?: return@withContext false

        try {
            val encFile = getEncryptedFile(vaultFile)

            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                encFile.openFileInput().use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: throw IllegalStateException("Could not open output stream to target MediaStore URI")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }

            // Cleanup encrypted files
            vaultFile.delete()
            vaultItem.thumbnailFilePath?.let { File(it).delete() }

            // Delete vault database entry
            db.vaultItemDao().deleteById(vaultItem.id)

            // Re-index into public MediaItem
            val destDir = when {
                isAudio -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SmartGallery")
                isVideo -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SmartGallery")
                else -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SmartGallery")
            }
            val finalFile = File(destDir, vaultItem.originalDisplayName)
            MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), arrayOf(vaultItem.originalMimeType)) { _, uri ->
                if (uri != null) {
                    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        val parsedId = runCatching { android.content.ContentUris.parseId(uri) }.getOrDefault(System.currentTimeMillis())
                        val restoredItem = MediaItem(
                            mediaStoreId = parsedId,
                            displayName = vaultItem.originalDisplayName,
                            absolutePath = finalFile.absolutePath,
                            mimeType = vaultItem.originalMimeType,
                            size = vaultItem.originalSize,
                            dateAdded = System.currentTimeMillis() / 1000L,
                            dateModified = System.currentTimeMillis() / 1000L,
                            dateTaken = System.currentTimeMillis(),
                            mediaType = vaultItem.originalMediaType,
                            bucketName = "SmartGallery",
                            albumName = "SmartGallery"
                        )
                        db.mediaItemDao().insert(restoredItem)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restoration error for ${vaultItem.originalDisplayName}: ${e.message}")
            context.contentResolver.delete(targetUri, null, null)
            false
        }
    }

    private fun generateThumbnailBitmap(item: MediaItem): Bitmap? {
        return try {
            if (item.mediaType.equals("VIDEO", ignoreCase = true)) {
                val retriever = MediaMetadataRetriever()
                val pfd = context.contentResolver.openFileDescriptor(item.contentUri, "r")
                pfd?.use {
                    retriever.setDataSource(it.fileDescriptor)
                    retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } else {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                context.contentResolver.openInputStream(item.contentUri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // ─── Legacy compatibility helpers ───────────────────────────────────────

    suspend fun moveToVault(mediaItem: MediaItem): File = withContext(Dispatchers.IO) {
        val staged = stageVaultEncryption(mediaItem)
        try {
            context.contentResolver.delete(mediaItem.contentUri, null, null)
        } catch (_: Exception) {}
        finalizeVaultMove(staged)
        staged.vaultFile
    }

    suspend fun restoreFromVault(mediaItem: MediaItem, destDir: File): File = withContext(Dispatchers.IO) {
        val vaultFile = File(mediaItem.absolutePath)
        val cleanName = vaultFile.name.removePrefix("enc_").removeSuffix(".aes")
        val restoredFile = File(destDir, cleanName)

        val encryptedFile = getEncryptedFile(vaultFile)

        encryptedFile.openFileInput().use { input ->
            FileOutputStream(restoredFile).use { output ->
                input.copyTo(output)
            }
        }

        db.mediaItemDao().setVaultItem(mediaItem.id, false)
        vaultFile.delete()
        restoredFile
    }

    // ─── 2. 30-Day Recycle Bin ──────────────────────────────────────────────

    suspend fun moveToTrash(mediaItem: MediaItem) = withContext(Dispatchers.IO) {
        db.mediaItemDao().setTrashed(mediaItem.id, true, System.currentTimeMillis())
        Log.i(TAG, "Moved item #${mediaItem.id} to 30-day Recycle Bin")
    }

    suspend fun restoreFromTrash(mediaItem: MediaItem) = withContext(Dispatchers.IO) {
        db.mediaItemDao().setTrashed(mediaItem.id, false, null)
        Log.i(TAG, "Restored item #${mediaItem.id} from Recycle Bin")
    }

    suspend fun permanentlyDelete(mediaItem: MediaItem) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(mediaItem.contentUri, null, null)
        } catch (_: Exception) {}
        try {
            val file = File(mediaItem.absolutePath)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
        db.mediaItemDao().deleteById(mediaItem.id)
        Log.i(TAG, "Permanently purged item #${mediaItem.id}")
    }

    // ─── 3. Deep EXIF Viewer & RGB Histogram ───────────────────────────────

    data class ExifDetails(
        val cameraMake: String,
        val cameraModel: String,
        val lensModel: String,
        val focalLength: String,
        val aperture: String,
        val iso: String,
        val shutterSpeed: String,
        val dateTimeOriginal: String,
        val gpsLatitude: Double?,
        val gpsLongitude: Double?,
        val flash: String,
        val whiteBalance: String,
        val dimensions: String
    )

    data class RgbHistogram(
        val red: IntArray = IntArray(256),
        val green: IntArray = IntArray(256),
        val blue: IntArray = IntArray(256),
        val luminance: IntArray = IntArray(256)
    )

    fun readExifDetails(path: String): ExifDetails {
        return try {
            val exif = ExifInterface(path)
            val latLong = exif.latLong
            val hasGps = latLong != null

            ExifDetails(
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE) ?: "Unknown",
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "Unknown",
                lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL) ?: "Standard Lens",
                focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: "N/A",
                aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" } ?: "N/A",
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "N/A",
                shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { "${it}s" } ?: "N/A",
                dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: "N/A",
                gpsLatitude = if (hasGps) latLong!![0] else null,
                gpsLongitude = if (hasGps) latLong!![1] else null,
                flash = exif.getAttribute(ExifInterface.TAG_FLASH) ?: "N/A",
                whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE) ?: "Auto",
                dimensions = "${exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: 0} x ${exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: 0}"
            )
        } catch (e: Exception) {
            ExifDetails("Unknown", "Unknown", "N/A", "N/A", "N/A", "N/A", "N/A", "N/A", null, null, "N/A", "N/A", "N/A")
        }
    }

    fun computeRgbHistogram(bitmap: Bitmap): RgbHistogram {
        val hist = RgbHistogram()
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (c in pixels) {
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)

            hist.red[r]++
            hist.green[g]++
            hist.blue[b]++
            hist.luminance[lum]++
        }
        return hist
    }

    // ─── 4. 1-Tap "Strip GPS/EXIF Before Sharing" Privacy Scrubber ─────────

    suspend fun stripGpsAndExif(inputPath: String): File = withContext(Dispatchers.IO) {
        val originalFile = File(inputPath)
        val tempDir = File(context.cacheDir, "SanitizedMedia")
        if (!tempDir.exists()) tempDir.mkdirs()

        val sanitizedFile = File(tempDir, "clean_${originalFile.name}")
        originalFile.copyTo(sanitizedFile, overwrite = true)

        try {
            val exif = ExifInterface(sanitizedFile.absolutePath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)

            exif.setAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, null)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, null)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, null)
            exif.setAttribute(ExifInterface.TAG_ARTIST, null)
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, null)
            exif.saveAttributes()

            Log.i(TAG, "Stripped GPS and metadata from ${sanitizedFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing EXIF: ${e.message}")
        }

        sanitizedFile
    }

    // ─── 5. "On This Day" Memories Maker ───────────────────────────────────

    suspend fun findOnThisDayMemories(): List<MediaItem> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val allItems = db.mediaItemDao().getAllSync()
        allItems.filter { item ->
            calendar.timeInMillis = item.dateTaken
            val itemMonth = calendar.get(Calendar.MONTH)
            val itemDay = calendar.get(Calendar.DAY_OF_MONTH)
            val itemYear = calendar.get(Calendar.YEAR)

            itemMonth == currentMonth && itemDay == currentDay && itemYear < currentYear
        }
    }

    // ─── 6. Set Wallpaper / Contact Photo Helper ───────────────────────────

    suspend fun setAsWallpaper(imagePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val wm = WallpaperManager.getInstance(context)
            val bitmap = BitmapFactory.decodeFile(File(imagePath).absolutePath)
            if (bitmap != null) {
                wm.setBitmap(bitmap)
                bitmap.recycle()
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wallpaper: ${e.message}")
            false
        }
    }
}
