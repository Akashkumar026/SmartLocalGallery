package com.example.smartgallery

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.smartgallery.data.db.DuplicateGroup
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.db.MediaItem
import com.example.smartgallery.data.db.PersonCluster
import com.example.smartgallery.data.settings.AppSettings
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.vault.VaultAndFileManager
import com.example.smartgallery.worker.GalleryIndexWorker
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import com.example.smartgallery.engine.SmartCategoryClassifier
import javax.inject.Inject

@Immutable
enum class SearchFilterChip(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    BILLS("Bills"),
    CERTIFICATES("Certificates"),
    SCREENSHOTS("Screenshots"),
    FAVORITES("Favorites")
}

@Immutable
enum class QuickDateChip(val label: String) {
    ALL_TIME("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_YEAR("Last Year")
}

@Immutable
data class AlbumGroup(
    val bucketId: Long,
    val bucketName: String,
    val coverMediaItem: MediaItem?,
    val itemCount: Int,
    val totalSizeBytes: Long
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val db: GalleryDatabase,
    private val vaultManager: VaultAndFileManager,
    private val engine: com.example.smartgallery.engine.LocalGalleryEngine,
    val profiler: com.example.smartgallery.profiler.DeviceCapabilityProfiler,
    val orchestrator: com.example.smartgallery.engine.ParallelTaskOrchestrator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val orchestratorState: StateFlow<com.example.smartgallery.engine.OrchestratorState> = orchestrator.state

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    val albums: StateFlow<List<AlbumGroup>> = _albums.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _personClusters = MutableStateFlow<List<PersonCluster>>(emptyList())
    val personClusters: StateFlow<List<PersonCluster>> = _personClusters.asStateFlow()

    private val _isFaceScanning = MutableStateFlow(false)
    val isFaceScanning: StateFlow<Boolean> = _isFaceScanning.asStateFlow()

    private val _isDocumentScanning = MutableStateFlow(false)
    val isDocumentScanning: StateFlow<Boolean> = _isDocumentScanning.asStateFlow()

    private val _trashedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashedItems: StateFlow<List<MediaItem>> = _trashedItems.asStateFlow()

    val vaultItems: StateFlow<List<MediaItem>> = db.mediaItemDao().getVaultItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _memories = MutableStateFlow<List<MediaItem>>(emptyList())
    val memories: StateFlow<List<MediaItem>> = _memories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilterChip.ALL)
    val searchFilter: StateFlow<SearchFilterChip> = _searchFilter.asStateFlow()

    private val _selectedDateChip = MutableStateFlow(QuickDateChip.ALL_TIME)
    val selectedDateChip: StateFlow<QuickDateChip> = _selectedDateChip.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customDateRange: StateFlow<Pair<Long, Long>?> = _customDateRange.asStateFlow()

    private val _selectedPersonClusterId = MutableStateFlow<Long?>(null)
    val selectedPersonClusterId: StateFlow<Long?> = _selectedPersonClusterId.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(
        listOf("Camera", "Screenshots", "Documents", "Receipts", "Favorites")
    )
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _pendingPermissions = MutableStateFlow<List<String>>(emptyList())
    val pendingPermissions: StateFlow<List<String>> = _pendingPermissions.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settings.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    init {
        // Observe settings for default startup tab
        viewModelScope.launch {
            settings.settingsFlow.collectLatest { s ->
                val tabIdx = when (s.defaultStartupTab) {
                    "ALBUMS" -> 1
                    "CLEANER" -> 2
                    "PLAYER" -> 3
                    "DOWNLOADER" -> 4
                    else -> 0
                }
                _selectedTab.value = tabIdx
            }
        }

        // Observe main media items (distinct to prevent whole-screen recomposition storms)
        viewModelScope.launch {
            db.mediaItemDao().getAll().distinctUntilChanged().collectLatest { items ->
                _mediaItems.value = items
                buildAlbums(items)
            }
        }

        // Observe duplicates
        viewModelScope.launch {
            db.duplicateGroupDao().getAll().distinctUntilChanged().collectLatest { groups ->
                _duplicateGroups.value = groups
            }
        }

        // Observe face clusters
        viewModelScope.launch {
            db.personClusterDao().getAll().distinctUntilChanged().collectLatest { clusters ->
                _personClusters.value = clusters
            }
        }

        // Observe trash
        viewModelScope.launch {
            db.mediaItemDao().getTrashedItems().distinctUntilChanged().collectLatest { items ->
                _trashedItems.value = items
            }
        }

        // Load memories
        loadMemories()
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index.coerceIn(0, 4)
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun computeDateRange(chip: QuickDateChip, custom: Pair<Long, Long>?): Pair<Long, Long> {
        if (custom != null) return custom
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        return when (chip) {
            QuickDateChip.ALL_TIME -> Pair(0L, Long.MAX_VALUE)
            QuickDateChip.TODAY -> {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            QuickDateChip.THIS_WEEK -> {
                cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
                Pair(cal.timeInMillis, now)
            }
            QuickDateChip.THIS_MONTH -> {
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            QuickDateChip.LAST_YEAR -> {
                val lastYear = cal.get(java.util.Calendar.YEAR) - 1
                cal.set(lastYear, java.util.Calendar.JANUARY, 1, 0, 0, 0)
                val start = cal.timeInMillis
                cal.set(lastYear, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
        }
    }

    fun triggerSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val query = _searchQuery.value.trim()
            val filter = _searchFilter.value
            val (startMs, endMs) = computeDateRange(_selectedDateChip.value, _customDateRange.value)
            val personId = _selectedPersonClusterId.value

            val mediaType = when (filter) {
                SearchFilterChip.PHOTOS -> "image"
                SearchFilterChip.VIDEOS -> "video"
                else -> null
            }

            val category = when (filter) {
                SearchFilterChip.DOCUMENTS -> SmartCategoryClassifier.CATEGORY_DOCUMENTS_IDS
                SearchFilterChip.BILLS -> SmartCategoryClassifier.CATEGORY_BILLS_RECEIPTS
                SearchFilterChip.CERTIFICATES -> SmartCategoryClassifier.CATEGORY_CERTIFICATES
                SearchFilterChip.SCREENSHOTS -> SmartCategoryClassifier.CATEGORY_SCREENSHOTS
                else -> null
            }

            val isFavoriteOnly = if (filter == SearchFilterChip.FAVORITES) 1 else 0

            val hasActiveFilter = query.isNotBlank() ||
                    filter != SearchFilterChip.ALL ||
                    _selectedDateChip.value != QuickDateChip.ALL_TIME ||
                    _customDateRange.value != null ||
                    personId != null

            if (!hasActiveFilter) {
                _searchResults.value = emptyList()
                return@launch
            }

            try {
                if (personId != null) {
                    db.personMediaCrossRefDao().getMediaForCluster(personId).collectLatest { personItems ->
                        val filtered = personItems.filter { item ->
                            val ts = if (item.dateTaken > 0) item.dateTaken else item.dateAdded * 1000L
                            val inDateRange = ts in startMs..endMs
                            val matchesType = mediaType == null || item.mimeType.startsWith(mediaType)
                            val matchesCat = category == null || item.smartCategory == category
                            val matchesFav = isFavoriteOnly == 0 || item.isFavorite
                            val matchesQuery = query.isBlank() ||
                                    item.displayName.contains(query, ignoreCase = true) ||
                                    item.absolutePath.contains(query, ignoreCase = true) ||
                                    item.bucketName.contains(query, ignoreCase = true) ||
                                    (item.ocrText?.contains(query, ignoreCase = true) == true) ||
                                    (item.smartCategory?.contains(query, ignoreCase = true) == true)
                            inDateRange && matchesType && matchesCat && matchesFav && matchesQuery && !item.isHidden && !item.isVaultItem && !item.isTrashed
                        }
                        _searchResults.value = filtered
                    }
                } else {
                    db.mediaItemDao().searchFiltered(
                        query = query,
                        mediaType = mediaType,
                        category = category,
                        isFavoriteOnly = isFavoriteOnly,
                        startDateMs = startMs,
                        endDateMs = endMs
                    ).collectLatest { results ->
                        _searchResults.value = results
                    }
                }
            } catch (e: Exception) {
                val fallback = _mediaItems.value.filter { item ->
                    val ts = if (item.dateTaken > 0) item.dateTaken else item.dateAdded * 1000L
                    (query.isBlank() || item.displayName.contains(query, ignoreCase = true)) &&
                            ts in startMs..endMs &&
                            !item.isHidden && !item.isVaultItem && !item.isTrashed
                }
                _searchResults.value = fallback
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        triggerSearch()
    }

    fun setSearchFilter(chip: SearchFilterChip) {
        _searchFilter.value = chip
        triggerSearch()
    }

    fun setQuickDateChip(chip: QuickDateChip) {
        _selectedDateChip.value = chip
        _customDateRange.value = null
        triggerSearch()
    }

    fun setCustomDateRange(startMs: Long, endMs: Long) {
        _customDateRange.value = Pair(startMs, endMs)
        _selectedDateChip.value = QuickDateChip.ALL_TIME
        triggerSearch()
    }

    fun setSelectedPersonClusterId(clusterId: Long?) {
        _selectedPersonClusterId.value = clusterId
        triggerSearch()
    }

    fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        val current = _recentSearches.value.filter { !it.equals(query.trim(), ignoreCase = true) }
        _recentSearches.value = (listOf(query.trim()) + current).take(10)
    }

    fun removeRecentSearch(query: String) {
        _recentSearches.value = _recentSearches.value.filter { !it.equals(query, ignoreCase = true) }
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
    }

    fun resetAllSearchFilters() {
        _searchQuery.value = ""
        _searchFilter.value = SearchFilterChip.ALL
        _selectedDateChip.value = QuickDateChip.ALL_TIME
        _customDateRange.value = null
        _selectedPersonClusterId.value = null
        _searchResults.value = emptyList()
    }

    fun hasStorageAccess(ctx: Context = context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasImages = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVideos = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasVisualSelected = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            hasImages || hasVideos || hasVisualSelected
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVideos = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            hasImages || hasVideos
        } else {
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startIndexing() {
        val missing = checkAndRequestPermissions(context)
        if (!hasStorageAccess(context)) {
            Log.w("MainViewModel", "Skipping background indexing until storage permission granted: $missing")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // 1. Immediately scan MediaStore on IO dispatcher so media grid displays right away
                val count = engine.scanAllMedia()
                Log.d("MainViewModel", "Immediate MediaStore scan complete: found $count items.")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error in immediate MediaStore scan: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }

            try {
                // 2. Schedule deeper background processing (xxHash, blur, duplicates, trash)
                val request = OneTimeWorkRequestBuilder<GalleryIndexWorker>().build()
                WorkManager.getInstance(context).enqueue(request)
                Log.d("MainViewModel", "GalleryIndexWorker enqueued successfully.")
            } catch (t: Throwable) {
                Log.e("MainViewModel", "Failed to enqueue GalleryIndexWorker: ${t.message}", t)
            }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            db.mediaItemDao().setFavorite(item.id, !item.isFavorite)
        }
    }

    fun toggleHidden(item: MediaItem) {
        viewModelScope.launch {
            db.mediaItemDao().setHidden(item.id, !item.isHidden)
        }
    }

    fun moveToTrash(item: MediaItem) {
        viewModelScope.launch {
            vaultManager.moveToTrash(item)
        }
    }

    fun restoreFromTrash(item: MediaItem) {
        viewModelScope.launch {
            vaultManager.restoreFromTrash(item)
        }
    }

    fun permanentlyDelete(item: MediaItem) {
        viewModelScope.launch {
            vaultManager.permanentlyDelete(item)
        }
    }

    fun restoreAllTrash() {
        viewModelScope.launch {
            val trashed = _trashedItems.value
            trashed.forEach { vaultManager.restoreFromTrash(it) }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val trashed = _trashedItems.value
            trashed.forEach { vaultManager.permanentlyDelete(it) }
        }
    }

    fun moveToVault(item: MediaItem) {
        viewModelScope.launch {
            vaultManager.moveToVault(item)
        }
    }

    fun restoreFromVault(item: MediaItem, destDir: File) {
        viewModelScope.launch {
            vaultManager.restoreFromVault(item, destDir)
        }
    }

    fun deleteDuplicateGroup(group: DuplicateGroup, keepId: Long) {
        viewModelScope.launch {
            try {
                val array = JSONArray(group.memberIdsJson)
                for (i in 0 until array.length()) {
                    val id = array.getLong(i)
                    if (id != keepId) {
                        val item = db.mediaItemDao().getByIdSync(id)
                        if (item != null) {
                            vaultManager.moveToTrash(item)
                        }
                    }
                }
                db.duplicateGroupDao().deleteById(group.id)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error resolving duplicate group: ${e.message}")
            }
        }
    }

    fun checkAndRequestPermissions(ctx: Context = context): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+)
            val hasImages = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVideos = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasVisualSelected = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

            if (!hasImages && !hasVisualSelected) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (!hasVideos && !hasVisualSelected) {
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (API 33)
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (hasStorageAccess(ctx)) {
            _pendingPermissions.value = emptyList()
        } else {
            _pendingPermissions.value = permissions
        }
        return permissions
    }

    fun deleteMedia(item: MediaItem, ctx: Context = context) {
        moveToTrash(item)
    }

    fun requestDeleteMedia(
        items: List<MediaItem>,
        onNeedConsent: (android.content.IntentSender) -> Unit,
        onDeleted: () -> Unit
    ) {
        viewModelScope.launch {
            when (val result = vaultManager.requestDeleteMedia(items)) {
                is VaultAndFileManager.DeleteResult.RequiresUserConsent -> {
                    onNeedConsent(result.intentSender)
                }
                is VaultAndFileManager.DeleteResult.Success -> {
                    onDeleted()
                }
                is VaultAndFileManager.DeleteResult.Error -> {
                    Log.w("MainViewModel", "Delete request warning: ${result.message}")
                    onDeleted()
                }
            }
        }
    }

    fun confirmDeleteMedia(items: List<MediaItem>) {
        viewModelScope.launch {
            vaultManager.onItemsDeletedFromStorage(items)
        }
    }

    fun updateClusterName(clusterId: Long, name: String) {
        viewModelScope.launch {
            db.personClusterDao().updateName(clusterId, name)
        }
    }

    fun reIndexFaces() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isFaceScanning.value) return@launch
            _isFaceScanning.value = true
            try {
                db.personMediaCrossRefDao().deleteAll()
                db.faceEmbeddingDao().deleteAll()
                db.personClusterDao().deleteAll()
                db.mediaItemDao().clearAllFaceEmbeddingIds()
                val threshold = settings.settingsFlow.first().faceClusterThreshold
                engine.scanFaces(threshold = threshold, forceRescan = true)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error re-indexing faces: ${e.message}", e)
            } finally {
                _isFaceScanning.value = false
            }
        }
    }

    fun mergeClusters(clusterA: PersonCluster, clusterB: PersonCluster) {
        mergeMultipleClusters(clusterA, listOf(clusterB))
    }

    fun mergeMultipleClusters(target: PersonCluster, sources: List<PersonCluster>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.mergeMultipleClustersManually(target, sources)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error merging person clusters: ${e.message}", e)
            }
        }
    }

    fun getPhotosForPersonCluster(clusterId: Long): Flow<List<MediaItem>> {
        return db.personMediaCrossRefDao().getMediaForCluster(clusterId)
    }

    fun scanFacesNow(forceRescan: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isFaceScanning.value) return@launch
            _isFaceScanning.value = true
            try {
                val threshold = settings.settingsFlow.first().faceClusterThreshold
                engine.scanFaces(threshold = threshold, forceRescan = forceRescan)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error running face scan: ${e.message}", e)
            } finally {
                _isFaceScanning.value = false
            }
        }
    }

    fun scanAndGroupDocumentsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isDocumentScanning.value) return@launch
            _isDocumentScanning.value = true
            try {
                val count = engine.scanAndGroupDocuments()
                Log.d("MainViewModel", "Categorized $count document media items.")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error scanning documents: ${e.message}", e)
            } finally {
                _isDocumentScanning.value = false
            }
        }
    }

    fun getItemsBySmartCategory(category: String): Flow<List<MediaItem>> {
        return db.mediaItemDao().getBySmartCategory(category)
    }

    fun getSmartAlbums(items: List<MediaItem>): List<AlbumGroup> {
        val list = mutableListOf<AlbumGroup>()
        val favorites = items.filter { it.isFavorite }
        if (favorites.isNotEmpty()) {
            list.add(AlbumGroup(-1, "Favorites", favorites.firstOrNull(), favorites.size, favorites.sumOf { it.size }))
        }
        val videos = items.filter { it.mediaType == "VIDEO" }
        if (videos.isNotEmpty()) {
            list.add(AlbumGroup(-2, "Videos", videos.firstOrNull(), videos.size, videos.sumOf { it.size }))
        }
        val camera = items.filter { it.bucketName.contains("camera", true) || it.absolutePath.contains("/DCIM/", true) }
        if (camera.isNotEmpty()) {
            list.add(AlbumGroup(-3, "Camera", camera.firstOrNull(), camera.size, camera.sumOf { it.size }))
        }
        val screenshots = items.filter { it.bucketName.contains("screenshot", true) || it.absolutePath.contains("screenshot", true) }
        if (screenshots.isNotEmpty()) {
            list.add(AlbumGroup(-4, "Screenshots", screenshots.firstOrNull(), screenshots.size, screenshots.sumOf { it.size }))
        }
        val downloads = items.filter { it.bucketName.contains("download", true) || it.absolutePath.contains("/Download", true) }
        if (downloads.isNotEmpty()) {
            list.add(AlbumGroup(-5, "Downloads", downloads.firstOrNull(), downloads.size, downloads.sumOf { it.size }))
        }
        return list
    }

    private fun buildAlbums(items: List<MediaItem>) {
        viewModelScope.launch(Dispatchers.Default) {
            val grouped = items.groupBy { it.bucketId }
            val albumList = grouped.map { (bucketId, bucketItems) ->
                AlbumGroup(
                    bucketId = bucketId,
                    bucketName = bucketItems.firstOrNull()?.bucketName ?: "Folder",
                    coverMediaItem = bucketItems.firstOrNull(),
                    itemCount = bucketItems.size,
                    totalSizeBytes = bucketItems.sumOf { it.size }
                )
            }.sortedByDescending { it.itemCount }

            _albums.value = albumList
        }
    }

    private fun loadMemories() {
        viewModelScope.launch {
            try {
                val list = vaultManager.findOnThisDayMemories()
                _memories.value = list
            } catch (t: Throwable) {
                Log.e("MainViewModel", "Failed to load on-this-day memories: ${t.message}")
            }
        }
    }
}

