package com.example.smartgallery.data.db

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.room.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

// ─── Entities ───────────────────────────────────────────────────────────────

@Immutable
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["mediaStoreId"]),
        Index(value = ["absolutePath"], unique = true),
        Index(value = ["bucketId"]),
        Index(value = ["dateAdded"]),
        Index(value = ["xxHash64"]),
        Index(value = ["isTrashed"]),
        Index(value = ["isVaultItem"]),
        Index(value = ["smartCategory"])
    ]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val displayName: String,
    val absolutePath: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val dateTaken: Long,
    val durationMs: Long = 0L, // 64-bit Long for 90+ hour support
    val width: Int = 0,
    val height: Int = 0,
    val bucketId: Long = 0,
    val bucketName: String = "",
    val thumbnailPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mediaType: String, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT"
    val isHidden: Boolean = false,
    val isFavorite: Boolean = false,
    val isVaultItem: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val ocrText: String? = null,
    val pHashHex: String? = null,
    val xxHash64: String? = null,
    val laplacianVariance: Float? = null,
    val isBestShot: Boolean = false,
    val faceEmbeddingIds: String = "[]",
    val albumName: String? = null,
    val smartCategory: String? = null,
    val isUserOverridden: Boolean = false,
    val hasSensitiveId: Boolean = false,
    val classifierVersion: Int = 0,
    val generationModified: Long = 0L
) {
    val contentUri: android.net.Uri
        get() = if (mediaStoreId > 0L) {
            when (mediaType.uppercase()) {
                "IMAGE" -> android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
                "VIDEO" -> android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
                "AUDIO" -> android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
                else -> android.content.ContentUris.withAppendedId(android.provider.MediaStore.Files.getContentUri("external"), mediaStoreId)
            }
        } else {
            android.net.Uri.parse(if (absolutePath.startsWith("content://")) absolutePath else "file://$absolutePath")
        }
}

@Entity(
    tableName = "face_embeddings",
    indices = [
        Index(value = ["mediaItemId"]),
        Index(value = ["personId"])
    ]
)
data class FaceEmbedding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaItemId: Long,
    val personId: Long,
    val embedding: ByteArray, // 192 L2-normalized floats
    val faceRectJson: String,
    val leftEyeX: Float,
    val leftEyeY: Float,
    val rightEyeX: Float,
    val rightEyeY: Float,
    val noseTipX: Float,
    val noseTipY: Float,
    val leftMouthX: Float,
    val leftMouthY: Float,
    val rightMouthX: Float,
    val rightMouthY: Float,
    val confidence: Float,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceEmbedding
        return id == other.id && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int = 31 * id.hashCode() + embedding.contentHashCode()
}

@Immutable
@Entity(tableName = "person_clusters")
data class PersonCluster(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val coverMediaId: Long = 0,
    val faceCount: Int = 0,
    val centroid: ByteArray? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PersonCluster) return false
        return id == other.id &&
                displayName == other.displayName &&
                coverMediaId == other.coverMediaId &&
                faceCount == other.faceCount &&
                createdAt == other.createdAt &&
                updatedAt == other.updatedAt &&
                ((centroid == null && other.centroid == null) || (centroid != null && other.centroid != null && centroid.contentEquals(other.centroid)))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + coverMediaId.hashCode()
        result = 31 * result + faceCount
        result = 31 * result + (centroid?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Immutable
@Entity(
    tableName = "person_media_cross_ref",
    primaryKeys = ["clusterId", "mediaId"],
    indices = [Index("clusterId"), Index("mediaId")]
)
data class PersonMediaCrossRef(
    val clusterId: Long,
    val mediaId: Long,
    val faceScore: Float = 0f
)

@Immutable
@Entity(
    tableName = "playback_states",
    indices = [Index(value = ["absolutePath"], unique = true)]
)
data class PlaybackState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val absolutePath: String,
    val positionMs: Long, // 64-bit Long
    val speed: Float = 1.0f,
    val audioTrackIndex: Int = 0,
    val subtitleTrackIndex: Int = -1,
    val brightness: Float = -1f,
    val volumeBoostPercent: Float = 100f,
    val lastPlayedAt: Long,
    val durationMs: Long,
    val completedCount: Int = 0,
    val bookmarksJson: String = "[]",
    val abRepeatAMs: Long = -1L,
    val abRepeatBMs: Long = -1L
)

@Immutable
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaItemId: Long,
    val positionMs: Long, // 64-bit Long
    val label: String,
    val createdAt: Long
)

@Immutable
@Entity(tableName = "duplicate_groups")
data class DuplicateGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupType: String, // "EXACT", "SIMILAR_IMAGE", "SIMILAR_FOLDER", "BLURRY_PHOTO"
    val memberIdsJson: String,
    val totalSizeBytes: Long,
    val suggestedKeepId: Long?,
    val detectedAt: Long,
    val jaccardScore: Float? = null,
    val dHashDistance: Int? = null,
    val laplacianVariance: Float? = null
)

@Immutable
@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["url"]),
        Index(value = ["status"])
    ]
)
data class DownloadTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val mediaType: String, // "VIDEO", "AUDIO", "INSTAGRAM_POST", "INSTAGRAM_REEL", "INSTAGRAM_CAROUSEL"
    val quality: String, // "4K", "1440p", "1080p", "720p", "480p", "AUDIO_ONLY"
    val status: String, // "QUEUED", "DOWNLOADING", "COMPLETED", "FAILED", "PAUSED"
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L, // 64-bit Long
    val totalBytes: Long = 0L, // 64-bit Long
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val outputPath: String = "",
    val isClipped: Boolean = false,
    val clipStartMs: Long = 0L, // 64-bit Long
    val clipEndMs: Long = 0L, // 64-bit Long
    val createdAt: Long,
    val completedAt: Long? = null,
    val errorMsg: String? = null
)

@Fts4
@Entity(tableName = "media_fts")
data class MediaFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long,
    val displayName: String,
    val ocrText: String,
    val bucketName: String,
    val detectedLabels: String = ""
)

@Immutable
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val vaultFilePath: String,
    val thumbnailFilePath: String? = null,
    val originalMediaStoreId: Long,
    val originalDisplayName: String,
    val originalMimeType: String,
    val originalSize: Long,
    val originalRelativePath: String,
    val originalBucketName: String,
    val originalMediaType: String,
    val originalDurationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val encryptedAt: Long = System.currentTimeMillis()
)

// ─── DAOs ───────────────────────────────────────────────────────────────────

@Dao
interface MediaItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItem>): List<Long>

    @Update
    suspend fun update(item: MediaItem)

    @Delete
    suspend fun delete(item: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun getById(id: Long): Flow<MediaItem?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getByIdSync(id: Long): MediaItem?

    @Query("SELECT * FROM media_items WHERE absolutePath = :path LIMIT 1")
    suspend fun getByPath(path: String): MediaItem?

    @Query("SELECT * FROM media_items WHERE mediaStoreId = :msId LIMIT 1")
    suspend fun getByMediaStoreId(msId: Long): MediaItem?

    @Query("SELECT * FROM media_items WHERE isHidden = 0 AND isVaultItem = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getAll(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isHidden = 0 AND isVaultItem = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    suspend fun getAllSync(): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE bucketId = :bucketId AND isHidden = 0 AND isVaultItem = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getByBucket(bucketId: Long): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE mediaType = :mediaType AND isHidden = 0 AND isVaultItem = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getByType(mediaType: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isHidden = 1 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getHidden(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isVaultItem = 1 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getVaultItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashedItems(): Flow<List<MediaItem>>

    @Query("""
        SELECT m.* FROM media_items m
        JOIN media_fts f ON m.id = f.rowid
        WHERE media_fts MATCH :query AND m.isHidden = 0 AND m.isVaultItem = 0 AND m.isTrashed = 0
        ORDER BY m.dateAdded DESC
    """)
    fun searchFts(query: String): Flow<List<MediaItem>>

    @Query("UPDATE media_items SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE media_items SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE media_items SET isVaultItem = :vault WHERE id = :id")
    suspend fun setVaultItem(id: Long, vault: Boolean)

    @Query("UPDATE media_items SET isTrashed = :trashed, trashedAt = :time WHERE id = :id")
    suspend fun setTrashed(id: Long, trashed: Boolean, time: Long?)

    @Query("UPDATE media_items SET ocrText = :text WHERE id = :id")
    suspend fun updateOcrText(id: Long, text: String)

    @Query("UPDATE media_items SET xxHash64 = :hash WHERE id = :id")
    suspend fun updateXxHash(id: Long, hash: String)

    @Query("UPDATE media_items SET pHashHex = :hash WHERE id = :id")
    suspend fun updatePHash(id: Long, hash: String)

    @Query("UPDATE media_items SET laplacianVariance = :variance, isBestShot = :isBest WHERE id = :id")
    suspend fun updateBlurMetrics(id: Long, variance: Float, isBest: Boolean)

    @Query("UPDATE media_items SET faceEmbeddingIds = :json WHERE id = :id")
    suspend fun updateFaceEmbeddingIds(id: Long, json: String)

    @Query("SELECT * FROM media_items WHERE smartCategory = :category AND isHidden = 0 AND isVaultItem = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getBySmartCategory(category: String): Flow<List<MediaItem>>

    @Query("UPDATE media_items SET smartCategory = :category WHERE id = :id AND isUserOverridden = 0")
    suspend fun updateSmartCategory(id: Long, category: String?)

    @Query("UPDATE media_items SET smartCategory = :category, classifierVersion = :version, hasSensitiveId = :hasSensitiveId, ocrText = :ocrText WHERE id = :id AND isUserOverridden = 0")
    suspend fun updateClassification(id: Long, category: String?, version: Int, hasSensitiveId: Boolean, ocrText: String?)

    @Query("UPDATE media_items SET smartCategory = :category, isUserOverridden = 1 WHERE id = :id")
    suspend fun setUserOverrideCategory(id: Long, category: String?)

    @Query("SELECT * FROM media_items WHERE (smartCategory IS NULL OR smartCategory = '') AND isUserOverridden = 0 AND mediaType = 'IMAGE' AND isTrashed = 0")
    suspend fun getUnclassifiedImages(): List<MediaItem>

    @Query("SELECT COUNT(*) FROM media_items WHERE smartCategory = :category AND isHidden = 0 AND isVaultItem = 0 AND isTrashed = 0")
    fun countBySmartCategory(category: String): Flow<Int>

    @Query("SELECT * FROM media_items WHERE xxHash64 = :hash AND isTrashed = 0")
    suspend fun getByXxHash(hash: String): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE xxHash64 IS NOT NULL AND isTrashed = 0")
    suspend fun getAllWithXxHash(): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE xxHash64 IS NULL AND isTrashed = 0")
    suspend fun getUnhashedItemsSync(): List<MediaItem>

    @Query("UPDATE media_items SET faceEmbeddingIds = '[]'")
    suspend fun clearAllFaceEmbeddingIds()

    @Query("DELETE FROM media_items WHERE isTrashed = 1 AND trashedAt < :cutoffTime")
    suspend fun purgeOldTrash(cutoffTime: Long): Int
}

@Dao
interface FaceEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: FaceEmbedding): Long

    @Query("DELETE FROM face_embeddings WHERE mediaItemId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("SELECT * FROM face_embeddings WHERE personId = :personId")
    fun getByPersonId(personId: Long): Flow<List<FaceEmbedding>>

    @Query("SELECT * FROM face_embeddings")
    suspend fun getAllEmbeddings(): List<FaceEmbedding>

    @Query("SELECT * FROM face_embeddings WHERE mediaItemId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): List<FaceEmbedding>

    @Query("UPDATE face_embeddings SET personId = :targetPersonId WHERE personId = :sourcePersonId")
    suspend fun repointPersonId(sourcePersonId: Long, targetPersonId: Long)

    @Query("DELETE FROM face_embeddings")
    suspend fun deleteAll()
}

@Dao
interface PersonClusterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cluster: PersonCluster): Long

    @Update
    suspend fun update(cluster: PersonCluster)

    @Query("DELETE FROM person_clusters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM person_clusters")
    suspend fun deleteAll()

    @Query("SELECT * FROM person_clusters ORDER BY faceCount DESC")
    fun getAll(): Flow<List<PersonCluster>>

    @Query("SELECT * FROM person_clusters ORDER BY faceCount DESC")
    suspend fun getAllSync(): List<PersonCluster>

    @Query("SELECT * FROM person_clusters WHERE id = :id")
    suspend fun getById(id: Long): PersonCluster?

    @Query("UPDATE person_clusters SET displayName = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateName(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE person_clusters SET faceCount = :faceCount, coverMediaId = :coverMediaId, centroid = :centroid, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateClusterStats(id: Long, faceCount: Int, coverMediaId: Long, centroid: ByteArray?, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface PersonMediaCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ref: PersonMediaCrossRef): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<PersonMediaCrossRef>): List<Long>

    @Query("SELECT DISTINCT m.* FROM media_items m INNER JOIN person_media_cross_ref ref ON m.id = ref.mediaId WHERE ref.clusterId = :clusterId AND m.isTrashed = 0 ORDER BY m.dateModified DESC")
    fun getMediaForCluster(clusterId: Long): Flow<List<MediaItem>>

    @Query("SELECT DISTINCT m.* FROM media_items m INNER JOIN person_media_cross_ref ref ON m.id = ref.mediaId WHERE ref.clusterId = :clusterId AND m.isTrashed = 0 ORDER BY m.dateModified DESC")
    suspend fun getMediaForClusterSync(clusterId: Long): List<MediaItem>

    @Query("INSERT OR IGNORE INTO person_media_cross_ref (clusterId, mediaId, faceScore) SELECT :targetClusterId, mediaId, faceScore FROM person_media_cross_ref WHERE clusterId = :sourceClusterId")
    suspend fun copyRefsToCluster(sourceClusterId: Long, targetClusterId: Long)

    @Query("DELETE FROM person_media_cross_ref WHERE clusterId = :clusterId")
    suspend fun deleteForCluster(clusterId: Long)

    @Transaction
    suspend fun repointCluster(sourceClusterId: Long, targetClusterId: Long) {
        copyRefsToCluster(sourceClusterId, targetClusterId)
        deleteForCluster(sourceClusterId)
    }

    @Query("DELETE FROM person_media_cross_ref")
    suspend fun deleteAll()
}

@Dao
interface PlaybackStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(state: PlaybackState): Long

    @Query("SELECT * FROM playback_states WHERE absolutePath = :path LIMIT 1")
    suspend fun getByPath(path: String): PlaybackState?

    @Query("UPDATE playback_states SET positionMs = :posMs, lastPlayedAt = :playedAt WHERE absolutePath = :path")
    suspend fun updatePosition(path: String, posMs: Long, playedAt: Long)

    @Query("SELECT * FROM playback_states ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<PlaybackState>
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks WHERE mediaItemId = :mediaId ORDER BY positionMs ASC")
    fun getByMediaId(mediaId: Long): Flow<List<Bookmark>>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT b.* FROM bookmarks b INNER JOIN media_items m ON b.mediaItemId = m.id WHERE m.absolutePath = :path ORDER BY b.positionMs ASC")
    suspend fun getByPath(path: String): List<Bookmark>
}

@Dao
interface DuplicateGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<DuplicateGroup>)

    @Query("DELETE FROM duplicate_groups")
    suspend fun deleteAll()

    @Query("SELECT * FROM duplicate_groups ORDER BY detectedAt DESC")
    fun getAll(): Flow<List<DuplicateGroup>>

    @Query("SELECT * FROM duplicate_groups WHERE groupType = :type ORDER BY detectedAt DESC")
    fun getByType(type: String): Flow<List<DuplicateGroup>>

    @Query("DELETE FROM duplicate_groups WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTask): Long

    @Update
    suspend fun update(task: DownloadTask)

    @Delete
    suspend fun delete(task: DownloadTask)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    fun getById(id: Long): Flow<DownloadTask?>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getByIdSync(id: Long): DownloadTask?

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY createdAt ASC")
    suspend fun getActiveTasks(): List<DownloadTask>

    @Query("UPDATE download_tasks SET progress = :progress, downloadedBytes = :downloaded, totalBytes = :total, speedBytesPerSec = :speed, etaSeconds = :eta WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, downloaded: Long, total: Long, speed: Long, eta: Long)

    @Query("UPDATE download_tasks SET status = :status, completedAt = :completedAt, errorMsg = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, completedAt: Long?, error: String?)

    @Query("UPDATE download_tasks SET status = :status, completedAt = :completedAt, outputPath = :outputPath, errorMsg = :error WHERE id = :id")
    suspend fun updateCompletion(id: Long, status: String, completedAt: Long?, outputPath: String?, error: String?)
}

@Dao
interface MediaFtsDao {
    @Query("INSERT OR REPLACE INTO media_fts(rowid, displayName, ocrText, bucketName, detectedLabels) VALUES(:id, :name, :ocr, :bucket, :labels)")
    suspend fun insert(id: Long, name: String, ocr: String, bucket: String, labels: String = "")

    @Query("DELETE FROM media_fts WHERE rowid = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM media_fts")
    suspend fun clearAll()
}

@Dao
interface VaultItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity): Long

    @Query("SELECT * FROM vault_items ORDER BY encryptedAt DESC")
    fun getAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items ORDER BY encryptedAt DESC")
    suspend fun getAllSync(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: Long): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): VaultItemEntity?

    @Delete
    suspend fun delete(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ─── Database Class ─────────────────────────────────────────────────────────

@Database(
    entities = [
        MediaItem::class,
        FaceEmbedding::class,
        PersonCluster::class,
        PersonMediaCrossRef::class,
        PlaybackState::class,
        Bookmark::class,
        DuplicateGroup::class,
        DownloadTask::class,
        MediaFts::class,
        VaultItemEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
    abstract fun personClusterDao(): PersonClusterDao
    abstract fun personMediaCrossRefDao(): PersonMediaCrossRefDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun duplicateGroupDao(): DuplicateGroupDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun mediaFtsDao(): MediaFtsDao
    abstract fun vaultItemDao(): VaultItemDao

    companion object {
        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        fun getInstance(context: Context): GalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "smart_local_gallery.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGalleryDatabase(@ApplicationContext context: Context): GalleryDatabase =
        GalleryDatabase.getInstance(context)

    @Provides
    fun provideMediaItemDao(db: GalleryDatabase): MediaItemDao = db.mediaItemDao()

    @Provides
    fun provideFaceEmbeddingDao(db: GalleryDatabase): FaceEmbeddingDao = db.faceEmbeddingDao()

    @Provides
    fun providePersonClusterDao(db: GalleryDatabase): PersonClusterDao = db.personClusterDao()

    @Provides
    fun providePersonMediaCrossRefDao(db: GalleryDatabase): PersonMediaCrossRefDao = db.personMediaCrossRefDao()

    @Provides
    fun providePlaybackStateDao(db: GalleryDatabase): PlaybackStateDao = db.playbackStateDao()

    @Provides
    fun provideBookmarkDao(db: GalleryDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideDuplicateGroupDao(db: GalleryDatabase): DuplicateGroupDao = db.duplicateGroupDao()

    @Provides
    fun provideDownloadTaskDao(db: GalleryDatabase): DownloadTaskDao = db.downloadTaskDao()

    @Provides
    fun provideMediaFtsDao(db: GalleryDatabase): MediaFtsDao = db.mediaFtsDao()

    @Provides
    fun provideVaultItemDao(db: GalleryDatabase): VaultItemDao = db.vaultItemDao()
}

