package com.example.smartgallery.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Scale
import coil3.size.Size
import com.example.smartgallery.AlbumGroup
import com.example.smartgallery.MainViewModel
import com.example.smartgallery.data.db.DuplicateGroup
import com.example.smartgallery.data.db.MediaItem
import com.example.smartgallery.data.db.PersonCluster
import com.example.smartgallery.engine.OrchestratorState
import com.example.smartgallery.engine.PipelineProgress
import com.example.smartgallery.engine.SmartCategoryClassifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.json.JSONArray
import java.io.File
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    navController: NavController
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val pendingPermissions by viewModel.pendingPermissions.collectAsStateWithLifecycle()
    val orchestratorState by viewModel.orchestratorState.collectAsStateWithLifecycle()
    var showOrchestratorSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val remaining = viewModel.checkAndRequestPermissions(context)
        val hasAnyGranted = grantResults.values.any { it }
        if (hasAnyGranted || remaining.isEmpty()) {
            viewModel.startIndexing()
        }
    }

    // Check permissions on mount and launch system permission prompt if required
    LaunchedEffect(Unit) {
        val needed = viewModel.checkAndRequestPermissions(context)
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.startIndexing()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                // Tab 0: Timeline
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Timeline") },
                    label = { Text("Timeline") }
                )
                // Tab 1: Albums
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Albums") },
                    label = { Text("Albums") }
                )
                // Tab 2: Cleaner
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    icon = {
                        val count = duplicateGroups.size
                        if (count > 0) {
                            BadgedBox(badge = { Badge { Text(count.toString()) } }) {
                                Icon(Icons.Default.CleaningServices, contentDescription = "Cleaner")
                            }
                        } else {
                            Icon(Icons.Default.CleaningServices, contentDescription = "Cleaner")
                        }
                    },
                    label = { Text("Cleaner") }
                )
                // Tab 3: Browse
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.setSelectedTab(3) },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Browse") },
                    label = { Text("Browse") }
                )
                // Tab 4: Downloader
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { viewModel.setSelectedTab(4) },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Downloader") },
                    label = { Text("Downloader") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "MainTabTransition"
            ) { tab ->
                when (tab) {
                    0 -> TimelineScreen(viewModel = viewModel, navController = navController)
                    1 -> AlbumsScreen(viewModel = viewModel, navController = navController)
                    2 -> CleanerScreen(viewModel = viewModel, navController = navController)
                                        3 -> MediaBrowserScreen(viewModel = viewModel, navController = navController)
                    4 -> com.example.smartgallery.ui.downloader.DownloaderScreen(navController = navController)
                }
            }

            // Permissions prompt banner if missing
            if (pendingPermissions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Storage permissions needed to index media",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { permissionLauncher.launch(pendingPermissions.toTypedArray()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }

            // Floating Multi-Task HUD Progress Pill
            if (orchestratorState.isAnyRunning) {
                Surface(
                    onClick = { showOrchestratorSheet = true },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "AI Orchestrator Active",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (showOrchestratorSheet) {
                com.example.smartgallery.ui.components.OrchestratorHudSheet(
                    state = orchestratorState,
                    onStartAll = { viewModel.orchestrator.startAllPipelines() },
                    onCancelAll = { viewModel.orchestrator.cancelAll() },
                    onStartDuplicates = { viewModel.orchestrator.startDuplicatesPipeline() },
                    onTogglePauseDuplicates = { viewModel.orchestrator.togglePauseDuplicates() },
                    onCancelDuplicates = { viewModel.orchestrator.cancelDuplicates() },
                    onStartCategorizer = { viewModel.orchestrator.startCategorizerPipeline() },
                    onTogglePauseCategorizer = { viewModel.orchestrator.togglePauseCategorizer() },
                    onCancelCategorizer = { viewModel.orchestrator.cancelCategorizer() },
                    onStartFaces = { viewModel.orchestrator.startFacesPipeline() },
                    onTogglePauseFaces = { viewModel.orchestrator.togglePauseFaces() },
                    onCancelFaces = { viewModel.orchestrator.cancelFaces() },
                    onDismiss = { showOrchestratorSheet = false }
                )
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// TAB 0: TimelineScreen
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showSlideshow by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        selectedIds = emptySet()
    }
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.setSearchQuery("")
    }

    val onThisDayItems = remember(items) {
        val calNow = java.util.Calendar.getInstance()
        val curMonth = calNow.get(java.util.Calendar.MONTH)
        val curDay = calNow.get(java.util.Calendar.DAY_OF_MONTH)
        val curYear = calNow.get(java.util.Calendar.YEAR)

        items.filter { item ->
            val ts = if (item.dateTaken > 0) item.dateTaken else item.dateAdded * 1000L
            if (ts <= 0L) return@filter false
            val calItem = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            calItem.get(java.util.Calendar.MONTH) == curMonth &&
            calItem.get(java.util.Calendar.DAY_OF_MONTH) == curDay &&
            calItem.get(java.util.Calendar.YEAR) < curYear &&
            !item.isHidden && !item.isVaultItem && !item.isTrashed
        }
    }

    val displayItems = remember(items, searchResults, searchQuery) {
        val raw = if (searchQuery.isNotBlank()) searchResults else items.filter { !it.isHidden && !it.isVaultItem }
        raw.distinctBy { it.id }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedIds = displayItems.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = {
                            val selectedItems = displayItems.filter { it.id in selectedIds }
                            selectedItems.forEach { viewModel.toggleFavorite(it) }
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Favorite, contentDescription = "Toggle Favorite")
                        }
                        IconButton(onClick = {
                            val selectedItems = displayItems.filter { it.id in selectedIds }
                            selectedItems.forEach { viewModel.moveToVault(it) }
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Lock, contentDescription = "Move to Vault")
                        }
                        IconButton(onClick = {
                            val selectedItems = displayItems.filter { it.id in selectedIds }
                            selectedItems.forEach { viewModel.deleteMedia(it, context) }
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        if (isSearchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search photos, OCR, tags...") },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Smart Gallery", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setSelectedTab(4) }) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "Downloader",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) viewModel.setSearchQuery("")
                        }) {
                            Icon(
                                if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                        IconButton(onClick = { viewModel.startIndexing() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan Media")
                        }
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && displayItems.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (displayItems.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank()) "No items match \"$searchQuery\""
                        else "No photos or videos found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.startIndexing() }) {
                        Text("Scan Device")
                    }
                }
            } else {
                // Group media by Month-Year for timeline
                val groupedByMonth = remember(displayItems) {
                    displayItems.groupBy { item ->
                        val timestamp = if (item.dateTaken > 0) item.dateTaken else item.dateAdded * 1000L
                        val date = Date(if (timestamp > 0) timestamp else System.currentTimeMillis())
                        DateFormat.format("MMMM yyyy", date).toString()
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(appSettings.gridColumns.coerceIn(2, 6)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    // On This Day Memories Banner Card
                    if (onThisDayItems.isNotEmpty() && searchQuery.isBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "On This Day",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { showSlideshow = true },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Play Slideshow")
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Rediscover moments from this day in past years (${onThisDayItems.size} photos)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(onThisDayItems.take(12), key = { it.id }) { memoryItem ->
                                            AsyncImage(
                                                model = memoryItem.contentUri,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { navigateToViewer(navController, memoryItem) },
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    groupedByMonth.forEach { (monthHeader, itemsInMonth) ->
                        // Header span
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = monthHeader,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(
                            items = itemsInMonth,
                            key = { item -> "${item.id}_${item.mediaStoreId}" }
                        ) { item ->
                            val isSelected = item.id in selectedIds
                            MediaThumbnailCard(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                targetThumbnailSize = viewModel.profiler.targetGridThumbnailSize,
                                preferredBitmapConfig = viewModel.profiler.preferredBitmapConfig,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                    } else {
                                        navigateToViewer(navController, item)
                                    }
                                },
                                onLongClick = {
                                    selectedIds = selectedIds + item.id
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSlideshow && onThisDayItems.isNotEmpty()) {
        MemoriesSlideshowDialog(
            photos = onThisDayItems,
            onDismiss = { showSlideshow = false }
        )
    }
}

@Composable
fun MemoriesSlideshowDialog(
    photos: List<MediaItem>,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    val animScale = remember { Animatable(1.0f) }

    LaunchedEffect(currentIndex) {
        animScale.snapTo(1.0f)
        animScale.animateTo(
            targetValue = 1.15f,
            animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
        )
    }

    LaunchedEffect(currentIndex, isPlaying) {
        if (isPlaying) {
            kotlinx.coroutines.delay(4000L)
            currentIndex = (currentIndex + 1) % photos.size
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val currentItem = photos[currentIndex]
            val timestamp = if (currentItem.dateTaken > 0) currentItem.dateTaken else currentItem.dateAdded * 1000L
            val dateStr = DateFormat.format("MMMM d, yyyy", Date(timestamp)).toString()
            val calItem = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val calNow = java.util.Calendar.getInstance()
            val yearsAgo = (calNow.get(java.util.Calendar.YEAR) - calItem.get(java.util.Calendar.YEAR)).coerceAtLeast(1)

            AnimatedContent(
                targetState = currentItem,
                transitionSpec = {
                    fadeIn(animationSpec = tween(800)) togetherWith fadeOut(animationSpec = tween(800))
                },
                modifier = Modifier.fillMaxSize(),
                label = "SlideshowCrossfade"
            ) { photo ->
                AsyncImage(
                    model = photo.contentUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = animScale.value
                            scaleY = animScale.value
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Top Header: Close + Counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Text(
                        "${currentIndex + 1} / ${photos.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Controls & Memory Label
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$yearsAgo ${if (yearsAgo == 1) "Year" else "Years"} Ago Today",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    dateStr,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            currentIndex = if (currentIndex > 0) currentIndex - 1 else photos.size - 1
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                    }

                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            currentIndex = (currentIndex + 1) % photos.size
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// TAB 1: AlbumsScreen
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val personClusters by viewModel.personClusters.collectAsStateWithLifecycle()
    val isFaceScanning by viewModel.isFaceScanning.collectAsStateWithLifecycle()
    val isDocumentScanning by viewModel.isDocumentScanning.collectAsStateWithLifecycle()
    val smartAlbums = remember(items) { viewModel.getSmartAlbums(items) }

    var selectedAlbum by remember { mutableStateOf<AlbumGroup?>(null) }
    var selectedPersonCluster by remember { mutableStateOf<PersonCluster?>(null) }
    var selectedSmartCategory by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editingCluster by remember { mutableStateOf<PersonCluster?>(null) }
    var editedName by remember { mutableStateOf("") }
    val context = LocalContext.current

    BackHandler(enabled = selectedAlbum != null) {
        selectedAlbum = null
    }
    BackHandler(enabled = selectedPersonCluster != null) {
        selectedPersonCluster = null
    }
    BackHandler(enabled = selectedSmartCategory != null) {
        selectedSmartCategory = null
    }

    if (selectedAlbum != null) {
        // Detailed Album view
        val album = selectedAlbum!!
        val filtered = remember(album, items) {
            if (album.bucketId < 0) {
                // Smart album match
                when (album.bucketName) {
                    "Favorites" -> items.filter { it.isFavorite }
                    "Videos" -> items.filter { it.mediaType.equals("VIDEO", ignoreCase = true) }
                    "Camera" -> items.filter { it.bucketName.contains("camera", true) || it.absolutePath.contains("/DCIM/Camera", true) }
                    "Screenshots" -> items.filter { it.bucketName.contains("screenshot", true) || it.absolutePath.contains("screenshot", true) }
                    "Downloads" -> items.filter { it.bucketName.contains("download", true) || it.absolutePath.contains("/Download", true) }
                    "WhatsApp" -> items.filter { it.bucketName.contains("whatsapp", true) || it.absolutePath.contains("WhatsApp", true) }
                    "Telegram" -> items.filter { it.bucketName.contains("telegram", true) || it.absolutePath.contains("Telegram", true) }
                    "Instagram" -> items.filter { it.bucketName.contains("instagram", true) || it.absolutePath.contains("Instagram", true) }
                    "Snapchat" -> items.filter { it.bucketName.contains("snapchat", true) || it.absolutePath.contains("Snapchat", true) }
                    else -> items
                }
            } else {
                items.filter { it.bucketId == album.bucketId }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(album.bucketName, fontWeight = FontWeight.Bold)
                            Text(
                                "${filtered.size} items â€¢ ${formatFileSize(album.totalSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedAlbum = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(2.dp)
            ) {
                items(filtered, key = { "${it.id}_${it.mediaStoreId}" }) { item ->
                    MediaThumbnailCard(
                        item = item,
                        isSelected = false,
                        isSelectionMode = false,
                        targetThumbnailSize = viewModel.profiler.targetGridThumbnailSize,
                        preferredBitmapConfig = viewModel.profiler.preferredBitmapConfig,
                        onClick = { navigateToViewer(navController, item) },
                        onLongClick = {},
                        onToggleFavorite = { viewModel.toggleFavorite(item) }
                    )
                }
            }
        }
        return
    }

    if (selectedPersonCluster != null) {
        // Detailed Person Cluster view
        val cluster = selectedPersonCluster!!
        val filtered = remember(cluster, items) {
            items.filter { item ->
                item.id == cluster.coverMediaId ||
                item.faceEmbeddingIds.contains("\"${cluster.id}\"") ||
                item.faceEmbeddingIds.contains(cluster.id.toString())
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(cluster.displayName, fontWeight = FontWeight.Bold)
                            Text("${filtered.size} photos", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedPersonCluster = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            editedName = cluster.displayName
                            editingCluster = cluster
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Name")
                        }
                    }
                )
            }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(2.dp)
            ) {
                items(filtered, key = { "${it.id}_${it.mediaStoreId}" }) { item ->
                    MediaThumbnailCard(
                        item = item,
                        isSelected = false,
                        isSelectionMode = false,
                        targetThumbnailSize = viewModel.profiler.targetGridThumbnailSize,
                        preferredBitmapConfig = viewModel.profiler.preferredBitmapConfig,
                        onClick = { navigateToViewer(navController, item) },
                        onLongClick = {}
                    )
                }
            }
        }
        return
    }

    // Cluster rename dialog
    if (editingCluster != null) {
        AlertDialog(
            onDismissRequest = { editingCluster = null },
            title = { Text("Rename Person") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editingCluster?.let { viewModel.updateClusterName(it.id, editedName) }
                    editingCluster = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCluster = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedSmartCategory != null) {
        val (catId, catTitle) = selectedSmartCategory!!
        val filtered = remember(catId, items) {
            when (catId) {
                SmartCategoryClassifier.CATEGORY_SCREENSHOTS -> items.filter {
                    it.smartCategory == catId || it.bucketName.contains("screenshot", true) || it.absolutePath.contains("screenshot", true)
                }
                SmartCategoryClassifier.CATEGORY_QR_BARCODES -> items.filter {
                    it.smartCategory == catId || it.displayName.contains("qr", true) || it.displayName.contains("barcode", true)
                }
                else -> items.filter { it.smartCategory == catId }
            }
        }

        var isCategorySelectionMode by remember { mutableStateOf(false) }
        var selectedCategoryIds by remember { mutableStateOf(emptySet<Long>()) }

        BackHandler(enabled = isCategorySelectionMode) {
            isCategorySelectionMode = false
            selectedCategoryIds = emptySet()
        }

        Scaffold(
            topBar = {
                if (isCategorySelectionMode) {
                    TopAppBar(
                        title = { Text("${selectedCategoryIds.size} selected", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = {
                                isCategorySelectionMode = false
                                selectedCategoryIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                selectedCategoryIds = if (selectedCategoryIds.size == filtered.size) emptySet() else filtered.map { it.id }.toSet()
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                            }
                            IconButton(onClick = {
                                val selectedItems = filtered.filter { it.id in selectedCategoryIds }
                                val uris = selectedItems.map { it.contentUri }
                                if (uris.isNotEmpty()) {
                                    val sendIntent = android.content.Intent().apply {
                                        if (uris.size == 1) {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                                            type = selectedItems.first().mimeType
                                        } else {
                                            action = android.content.Intent.ACTION_SEND_MULTIPLE
                                            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                                            type = "*/*"
                                        }
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Media"))
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                            IconButton(onClick = {
                                val selectedItems = filtered.filter { it.id in selectedCategoryIds }
                                selectedItems.forEach { viewModel.moveToVault(it) }
                                isCategorySelectionMode = false
                                selectedCategoryIds = emptySet()
                            }) {
                                Icon(Icons.Default.Lock, contentDescription = "Move to Vault")
                            }
                            IconButton(onClick = {
                                val selectedItems = filtered.filter { it.id in selectedCategoryIds }
                                selectedItems.forEach { viewModel.deleteMedia(it, context) }
                                isCategorySelectionMode = false
                                selectedCategoryIds = emptySet()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text(catTitle, fontWeight = FontWeight.Bold)
                                Text(
                                    "${filtered.size} items",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedSmartCategory = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (filtered.isNotEmpty()) {
                                IconButton(onClick = { isCategorySelectionMode = true }) {
                                    Icon(Icons.Default.Checklist, contentDescription = "Select items")
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No items categorized under $catTitle yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = { viewModel.scanAndGroupDocumentsNow() }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan & Group Documents")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = padding,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = filtered, key = { it.id }) { item ->
                        MediaThumbnailCard(
                            item = item,
                            isSelected = item.id in selectedCategoryIds,
                            isSelectionMode = isCategorySelectionMode,
                            onClick = {
                                if (isCategorySelectionMode) {
                                    selectedCategoryIds = if (item.id in selectedCategoryIds) {
                                        selectedCategoryIds - item.id
                                    } else {
                                        selectedCategoryIds + item.id
                                    }
                                } else {
                                    if (item.mediaType.equals("VIDEO", ignoreCase = true)) {
                                        navController.navigate("player/${item.id}")
                                    } else {
                                        navController.navigate("viewer/${item.id}")
                                    }
                                }
                            },
                            onLongClick = {
                                isCategorySelectionMode = true
                                selectedCategoryIds = selectedCategoryIds + item.id
                            }
                        )
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albums & Folders", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.scanAndGroupDocumentsNow() }) {
                        if (isDocumentScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Scan & Group Documents")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Section 0: Smart Document & Media Collections ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Smart Document Collections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isDocumentScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Scanning OCR...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.scanAndGroupDocumentsNow() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Scan & Group", fontSize = 12.sp)
                        }
                    }
                }

                val categoryCounts = remember(items) {
                    val map = mutableMapOf<String, Int>()
                    for (meta in SmartCategoryClassifier.ALL_CATEGORIES) {
                        val count = when (meta.id) {
                            SmartCategoryClassifier.CATEGORY_SCREENSHOTS -> items.count {
                                it.smartCategory == meta.id || it.bucketName.contains("screenshot", true) || it.absolutePath.contains("screenshot", true)
                            }
                            SmartCategoryClassifier.CATEGORY_QR_BARCODES -> items.count {
                                it.smartCategory == meta.id || it.displayName.contains("qr", true) || it.displayName.contains("barcode", true)
                            }
                            else -> items.count { it.smartCategory == meta.id }
                        }
                        map[meta.id] = count
                    }
                    map
                }

                val populatedCategories = remember(categoryCounts) {
                    SmartCategoryClassifier.ALL_CATEGORIES
                        .map { it to (categoryCounts[it.id] ?: 0) }
                        .filter { it.second > 0 }
                }

                if (populatedCategories.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Smart Document & Object Classifier",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Tap to categorize into 12 practical collections",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.scanAndGroupDocumentsNow() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Classify", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        populatedCategories.chunked(2).forEach { rowPair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPair.forEach { (meta, count) ->
                                    ElevatedCard(
                                        onClick = { selectedSmartCategory = Pair(meta.id, meta.title) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(76.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = meta.icon,
                                                        contentDescription = meta.title,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = meta.title,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$count items",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowPair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            // â”€â”€ Section 1: Smart Albums â”€â”€
            if (smartAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = "Smart Albums",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            ElevatedCard(
                                onClick = { navController.navigate("vault") },
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(160.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = "Vault",
                                                tint = MaterialTheme.colorScheme.onTertiary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Private Vault",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "AES-256 Locked",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                        items(items = smartAlbums, key = { it.bucketId }) { smartAlbum ->
                            ElevatedCard(
                                onClick = { selectedAlbum = smartAlbum },
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(160.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        if (smartAlbum.coverMediaItem != null) {
                                            AsyncImage(
                                                model = smartAlbum.coverMediaItem.thumbnailPath
                                                    ?: smartAlbum.coverMediaItem.absolutePath,
                                                contentDescription = smartAlbum.bucketName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            smartAlbum.bucketName,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "${smartAlbum.itemCount} items",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Section 2: Person Clusters (Faces) ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "People & Pets (${personClusters.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isFaceScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Scanning AI...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        TextButton(onClick = { viewModel.scanFacesNow() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (personClusters.isEmpty()) "Scan Faces" else "Re-scan", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (personClusters.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = personClusters, key = { it.id }) { cluster ->
                            val coverItem = items.firstOrNull { it.id == cluster.coverMediaId }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(80.dp)
                                    .combinedClickable(
                                        onClick = { selectedPersonCluster = cluster },
                                        onLongClick = {
                                            editedName = cluster.displayName
                                            editingCluster = cluster
                                        }
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (coverItem != null) {
                                        AsyncImage(
                                            model = coverItem.contentUri,
                                            contentDescription = cluster.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Face,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    cluster.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${cluster.faceCount}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Auto Group by Faces",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    if (isFaceScanning) "Running offline AI face clustering..." else "Group your photos automatically using offline on-device AI.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isFaceScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Button(onClick = { viewModel.scanFacesNow() }) {
                                    Text("Scan Now", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // â”€â”€ Section 3: Device Folders â”€â”€
            item {
                Text(
                    text = "Folders on Device (${albums.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }

            items(
                items = albums.chunked(2),
                key = { pair -> pair.joinToString(separator = "_") { it.bucketId.toString() } }
            ) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { albumGroup ->
                        ElevatedCard(
                            onClick = { selectedAlbum = albumGroup },
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    if (albumGroup.coverMediaItem != null) {
                                        AsyncImage(
                                            model = albumGroup.coverMediaItem.thumbnailPath
                                                ?: albumGroup.coverMediaItem.absolutePath,
                                            contentDescription = albumGroup.bucketName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Folder, contentDescription = null)
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        albumGroup.bucketName,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "${albumGroup.itemCount} items â€¢ ${formatFileSize(albumGroup.totalSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// TAB 2: CleanerScreen
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val trashedItems by viewModel.trashedItems.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    var selectedCleanerTab by remember { mutableStateOf(0) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var itemToDeletePermanently by remember { mutableStateOf<MediaItem?>(null) }

    val exactGroups = remember(duplicateGroups) { duplicateGroups.filter { it.groupType == "EXACT" } }
    val similarImages = remember(duplicateGroups) { duplicateGroups.filter { it.groupType == "SIMILAR_IMAGE" } }
    val similarFolders = remember(duplicateGroups) { duplicateGroups.filter { it.groupType == "SIMILAR_FOLDER" } }

    val totalSavingsBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { group ->
            val members = parseMemberIds(group.memberIdsJson)
            if (members.size > 1) {
                // Approximate saving: (members.size - 1) * average size
                val excess = (members.size - 1)
                (group.totalSizeBytes / members.size.coerceAtLeast(1)) * excess
            } else 0L
        }
    }

    var groupToDeleteFolder by remember { mutableStateOf<DuplicateGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Cleaner", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.orchestrator.startAllPipelines() }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Run Concurrent AI Orchestrator")
                    }
                    IconButton(onClick = { viewModel.startIndexing() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan Media")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.orchestrator.startAllPipelines() },
                icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                text = { Text("Scan All (Concurrent AI)") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // â”€â”€ Top Savings Banner â”€â”€
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Potential Savings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatFileSize(totalSavingsBytes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${duplicateGroups.size} duplicate groups detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // â”€â”€ Sub-tabs â”€â”€
            TabRow(selectedTabIndex = selectedCleanerTab) {
                Tab(
                    selected = selectedCleanerTab == 0,
                    onClick = { selectedCleanerTab = 0 },
                    text = { Text("Exact (${exactGroups.size})") }
                )
                Tab(
                    selected = selectedCleanerTab == 1,
                    onClick = { selectedCleanerTab = 1 },
                    text = { Text("Similar (${similarImages.size})") }
                )
                Tab(
                    selected = selectedCleanerTab == 2,
                    onClick = { selectedCleanerTab = 2 },
                    text = { Text("Folders (${similarFolders.size})") }
                )
                Tab(
                    selected = selectedCleanerTab == 3,
                    onClick = { selectedCleanerTab = 3 },
                    text = { Text("Trash (${trashedItems.size})") }
                )
            }

            // â”€â”€ Content â”€â”€
            when (selectedCleanerTab) {
                0 -> {
                    // Exact Duplicates
                    if (exactGroups.isEmpty()) {
                        EmptyCleanerState("No exact duplicates found!")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(exactGroups, key = { it.id }) { group ->
                                DuplicateGroupCard(
                                    group = group,
                                    mediaItems = mediaItems,
                                    onDeleteOthers = { keepId ->
                                        viewModel.deleteDuplicateGroup(group, keepId)
                                    },
                                    onDeleteAll = {
                                        viewModel.deleteDuplicateGroup(group, -1L)
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Similar Photos
                    if (similarImages.isEmpty()) {
                        EmptyCleanerState("No similar photos or bursts found!")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(similarImages, key = { it.id }) { group ->
                                DuplicateGroupCard(
                                    group = group,
                                    mediaItems = mediaItems,
                                    extraInfo = "dHash distance: ${group.dHashDistance ?: 0}",
                                    onDeleteOthers = { keepId ->
                                        viewModel.deleteDuplicateGroup(group, keepId)
                                    },
                                    onDeleteAll = {
                                        viewModel.deleteDuplicateGroup(group, -1L)
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Similar Folders
                    if (similarFolders.isEmpty()) {
                        EmptyCleanerState("No similar folders detected!")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(similarFolders, key = { it.id }) { group ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        val jaccardPercent = ((group.jaccardScore ?: 0f) * 100).toInt()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Folder Duplicate",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("$jaccardPercent% Match") }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Total size: ${formatFileSize(group.totalSizeBytes)}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = { groupToDeleteFolder = group },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Delete Duplicate Folder")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Recycle Bin
                    if (trashedItems.isEmpty()) {
                        EmptyCleanerState("Recycle Bin is empty!")
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Batch Action Bar
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${trashedItems.size} items in trash",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Auto-purges after ${appSettings.trashAutoPurgeDays} days",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.restoreAllTrash() },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Restore All", style = MaterialTheme.typography.labelMedium)
                                        }
                                        Button(
                                            onClick = { showEmptyTrashDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Empty", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }

                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(trashedItems, key = { it.id }) { item ->
                                    val now = System.currentTimeMillis()
                                    val trashedAt = item.trashedAt ?: now
                                    val daysSinceTrashed = ((now - trashedAt) / (1000L * 60 * 60 * 24)).toInt()
                                    val daysLeft = maxOf(0, appSettings.trashAutoPurgeDays - daysSinceTrashed)

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(item.contentUri)
                                                        .crossfade(true)
                                                        .size(Size(320, 320))
                                                        .bitmapConfig(Bitmap.Config.RGB_565)
                                                        .build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                // Countdown badge
                                                Surface(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(4.dp),
                                                    color = if (daysLeft <= 3) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "${daysLeft}d left",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (daysLeft <= 3) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatFileSize(item.size)} • $daysLeft days left",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (daysLeft <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            IconButton(onClick = { viewModel.restoreFromTrash(item) }) {
                                                Icon(
                                                    Icons.Default.Restore,
                                                    contentDescription = "Restore",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = { itemToDeletePermanently = item }) {
                                                Icon(
                                                    Icons.Default.DeleteForever,
                                                    contentDescription = "Delete Permanently",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (groupToDeleteFolder != null) {
        val group = groupToDeleteFolder!!
        AlertDialog(
            onDismissRequest = { groupToDeleteFolder = null },
            title = { Text("Delete Entire Folder?") },
            text = { Text("Are you sure you want to delete this folder and its duplicate contents? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val suggestedKeep = group.suggestedKeepId ?: -1L
                        viewModel.deleteDuplicateGroup(group, suggestedKeep)
                        groupToDeleteFolder = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDeleteFolder = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Empty Recycle Bin?") },
            text = { Text("Permanently delete all ${trashedItems.size} items? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyTrashDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (itemToDeletePermanently != null) {
        val item = itemToDeletePermanently!!
        AlertDialog(
            onDismissRequest = { itemToDeletePermanently = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Permanently?") },
            text = { Text("Are you sure you want to permanently delete \"${item.displayName}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentlyDelete(item)
                        itemToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeletePermanently = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    mediaItems: List<MediaItem>,
    extraInfo: String? = null,
    onDeleteOthers: (Long) -> Unit,
    onDeleteAll: () -> Unit
) {
    val memberIds = remember(group.memberIdsJson) { parseMemberIds(group.memberIdsJson) }
    val items = remember(memberIds, mediaItems) {
        mediaItems.filter { it.id in memberIds }
    }
    val suggestedKeepId = group.suggestedKeepId ?: items.firstOrNull()?.id ?: 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${items.size} Duplicates â€¢ ${formatFileSize(group.totalSizeBytes)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (extraInfo != null) {
                    Text(
                        text = extraInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thumbnails row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = items, key = { it.id }) { item ->
                    val isKeep = item.id == suggestedKeepId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(100.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (isKeep) Modifier.background(Color(0xFF2E7D32))
                                    else Modifier
                                )
                                .padding(if (isKeep) 3.dp else 0.dp)
                        ) {
                            AsyncImage(
                                model = item.thumbnailPath ?: item.absolutePath,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            if (isKeep) {
                                Surface(
                                    color = Color(0xFF2E7D32),
                                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        "KEEP",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            formatFileSize(item.size),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            item.displayName,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDeleteAll) {
                    Text("Delete All")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onDeleteOthers(suggestedKeepId) }) {
                    Text("Delete Others")
                }
            }
        }
    }
}

@Composable
private fun EmptyCleanerState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your device storage is well organized.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// TAB 3: MediaBrowserScreen
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowserScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    var selectedMediaTypeTab by remember { mutableStateOf(0) }
    var isGridView by remember { mutableStateOf(true) }

    val categories = listOf("All", "Videos", "Images", "Audio", "Documents")

    val filteredItems = remember(items, selectedMediaTypeTab) {
        when (selectedMediaTypeTab) {
            1 -> items.filter { it.mediaType.equals("VIDEO", ignoreCase = true) }
            2 -> items.filter { it.mediaType.equals("IMAGE", ignoreCase = true) }
            3 -> items.filter { it.mediaType.equals("AUDIO", ignoreCase = true) }
            4 -> items.filter { it.mediaType.equals("DOCUMENT", ignoreCase = true) }
            else -> items
        }.filter { !it.isHidden && !it.isVaultItem }
    }

    // SAF Picker
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val encoded = Uri.encode(it.toString())
            navController.navigate("player/$encoded")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse Files", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                text = { Text("Open File") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedMediaTypeTab,
                edgePadding = 16.dp
            ) {
                categories.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedMediaTypeTab == index,
                        onClick = { selectedMediaTypeTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No files found in ${categories[selectedMediaTypeTab]}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(appSettings.gridColumns.coerceIn(2, 6)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(filteredItems, key = { "${it.id}_${it.mediaStoreId}" }) { item ->
                        MediaThumbnailCard(
                            item = item,
                            isSelected = false,
                            isSelectionMode = false,
                            targetThumbnailSize = viewModel.profiler.targetGridThumbnailSize,
                            preferredBitmapConfig = viewModel.profiler.preferredBitmapConfig,
                            onClick = { navigateToViewer(navController, item) },
                            onLongClick = {},
                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredItems, key = { "${it.id}_${it.mediaStoreId}" }) { item ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text("${formatFileSize(item.size)} â€¢ ${item.bucketName}")
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    AsyncImage(
                                        model = item.thumbnailPath ?: item.absolutePath,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.toggleFavorite(item) }) {
                                    Icon(
                                        if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (item.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { navigateToViewer(navController, item) },
                                onLongClick = {}
                            )
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Shared Components & Navigation Helpers
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnailCard(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    targetThumbnailSize: Int = 320,
    preferredBitmapConfig: Bitmap.Config = Bitmap.Config.RGB_565,
    onToggleFavorite: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isImage = item.mediaType.equals("IMAGE", ignoreCase = true)
    val isVideo = item.mediaType.equals("VIDEO", ignoreCase = true)
    val isGif = item.mimeType.contains("gif", ignoreCase = true)

    val imageRequest = remember(item.id, item.thumbnailPath, item.contentUri, targetThumbnailSize, preferredBitmapConfig) {
        ImageRequest.Builder(context)
            .data(item.thumbnailPath ?: item.contentUri)
            .bitmapConfig(preferredBitmapConfig)
            .size(Size(targetThumbnailSize, targetThumbnailSize))
            .scale(Scale.FILL)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Selection overlay
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else Color.Black.copy(alpha = 0.15f)
                    )
            )
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
            )
        }

        // Favorite icon overlay
        if (item.isFavorite && !isSelectionMode) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = Color(0xFFE91E63),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
            )
        }

        // GIF badge
        if (isGif) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
            ) {
                Text(
                    text = "GIF",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Video duration badge
        if (isVideo && item.durationMs > 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = formatDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

fun navigateToViewer(navController: NavController, item: MediaItem) {
    val encoded = Uri.encode(item.absolutePath)
    when (item.mediaType.uppercase()) {
        "IMAGE" -> navController.navigate("image_viewer/$encoded")
        "VIDEO" -> navController.navigate("player/$encoded")
        "AUDIO" -> navController.navigate("player/$encoded")
        "DOCUMENT" -> {
            if (item.displayName.endsWith(".pdf", ignoreCase = true)) {
                navController.navigate("pdf_viewer/$encoded")
            } else {
                navController.navigate("text_viewer/$encoded")
            }
        }
        else -> {
            if (item.displayName.endsWith(".pdf", ignoreCase = true)) {
                navController.navigate("pdf_viewer/$encoded")
            } else if (item.displayName.endsWith(".txt", ignoreCase = true) ||
                item.displayName.endsWith(".md", ignoreCase = true) ||
                item.displayName.endsWith(".json", ignoreCase = true)
            ) {
                navController.navigate("text_viewer/$encoded")
            } else {
                navController.navigate("image_viewer/$encoded")
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return "%.1f %s".format(Locale.US, bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min >= 60) {
        val hr = min / 60
        val remMin = min % 60
        "%d:%02d:%02d".format(Locale.US, hr, remMin, sec)
    } else {
        "%02d:%02d".format(Locale.US, min, sec)
    }
}

private fun parseMemberIds(json: String): List<Long> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getLong(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

