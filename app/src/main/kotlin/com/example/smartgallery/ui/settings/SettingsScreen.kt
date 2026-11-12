package com.example.smartgallery.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.AppSettings
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.worker.GalleryIndexWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import javax.inject.Inject

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// VIEWMODEL
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settingsFlow: Flow<AppSettings> = settings.settingsFlow

    private val _cacheSize = MutableStateFlow("Calculating...")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    val isIndexingFlow: Flow<Boolean> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow("gallery_index_once")
        .map { list ->
            list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }

    init {
        refreshCacheSize()
    }

    // â”€â”€ Appearance delegations â”€â”€

    fun setThemeMode(m: String) = viewModelScope.launch {
        settings.setThemeMode(m)
    }

    fun setDynamicColor(b: Boolean) = viewModelScope.launch {
        settings.setDynamicColor(b)
    }

    fun setGridColumns(n: Int) = viewModelScope.launch {
        settings.setGridColumns(n)
    }

    fun setThumbnailCropSquare(b: Boolean) = viewModelScope.launch {
        settings.setThumbnailCropSquare(b)
    }

    fun setAutoPlayGifs(b: Boolean) = viewModelScope.launch {
        settings.setAutoPlayGifs(b)
    }

    fun setDefaultStartupTab(tab: String) = viewModelScope.launch {
        settings.setDefaultStartupTab(tab)
    }

    // â”€â”€ AI & scan delegations â”€â”€

    fun setFaceClusterThreshold(t: Float) = viewModelScope.launch {
        settings.setFaceClusterThreshold(t)
    }

    fun setOcrMinChars(n: Int) = viewModelScope.launch {
        settings.setOcrMinChars(n)
    }

    fun setDuplicateJaccardThreshold(t: Float) = viewModelScope.launch {
        settings.setDuplicateJaccardThreshold(t)
    }

    fun setBgScanSchedule(s: String) = viewModelScope.launch {
        settings.setBgScanSchedule(s)
    }

    fun addExcludedFolder(path: String, currentFoldersJson: String) {
        val list = parseJsonFolderList(currentFoldersJson).toMutableList()
        if (!list.contains(path)) {
            list.add(path)
            viewModelScope.launch {
                settings.updateExcludedFolders(list)
            }
        }
    }

    fun removeExcludedFolder(path: String, currentFoldersJson: String) {
        val list = parseJsonFolderList(currentFoldersJson).toMutableList()
        if (list.remove(path)) {
            viewModelScope.launch {
                settings.updateExcludedFolders(list)
            }
        }
    }

    fun reclusterFaces() {
        GalleryIndexWorker.enqueueOneTime(context)
    }

    // â”€â”€ Video & audio delegations â”€â”€

    fun setDecoderPriority(p: String) = viewModelScope.launch {
        settings.setDecoderPriority(p)
    }

    fun setResumeBehavior(b: String) = viewModelScope.launch {
        settings.setResumeBehavior(b)
    }

    fun setDoubleTapSeekSecs(s: Int) = viewModelScope.launch {
        settings.setDoubleTapSeekSecs(s)
    }

    fun setLongPressSpeed(speed: Float) = viewModelScope.launch {
        settings.setLongPressSpeed(speed)
    }

    fun setRememberBrightness(b: Boolean) = viewModelScope.launch {
        settings.setRememberBrightness(b)
    }

    fun setRememberSpeed(b: Boolean) = viewModelScope.launch {
        settings.setRememberSpeed(b)
    }

    fun setRememberAudioBoost(b: Boolean) = viewModelScope.launch {
        settings.setRememberAudioBoost(b)
    }

    fun setAutoPipOnHome(b: Boolean) = viewModelScope.launch {
        settings.setAutoPipOnHome(b)
    }

    fun setBackgroundAudioOnScreenOff(b: Boolean) = viewModelScope.launch {
        settings.setBackgroundAudioOnScreenOff(b)
    }

    fun setPlaylistEndAction(a: String) = viewModelScope.launch {
        settings.setPlaylistEndAction(a)
    }

    // â”€â”€ Subtitle & audio language delegations â”€â”€

    fun setPreferredAudioLang(l: String) = viewModelScope.launch {
        settings.setPreferredAudioLang(l)
    }

    fun setPreferredSubtitleLang1(l: String) = viewModelScope.launch {
        settings.setPreferredSubtitleLang1(l)
    }

    fun setPreferredSubtitleLang2(l: String) = viewModelScope.launch {
        settings.setPreferredSubtitleLang2(l)
    }

    fun setAutoSubtitleForeign(b: Boolean) = viewModelScope.launch {
        settings.setAutoSubtitleForeign(b)
    }

    fun setSubtitleFontSize(size: Int) = viewModelScope.launch {
        settings.setSubtitleFontSize(size)
    }

    fun setSubtitleColorArgb(color: Int) = viewModelScope.launch {
        settings.setSubtitleColorArgb(color)
    }

    fun setSubtitleBgOpacity(opacity: Float) = viewModelScope.launch {
        settings.setSubtitleBgOpacity(opacity)
    }

    fun setSubtitleEncoding(enc: String) = viewModelScope.launch {
        settings.setSubtitleEncoding(enc)
    }

    fun setSubtitleSaveDir(dir: String) = viewModelScope.launch {
        settings.setSubtitleSaveDir(dir)
    }

    // Downloader & Network Privacy
    fun setStrictOfflineLockdown(enabled: Boolean) = viewModelScope.launch {
        settings.setStrictOfflineLockdown(enabled)
    }

    fun setDownloadAria2cConnections(conn: Int) = viewModelScope.launch {
        settings.setDownloadAria2cConnections(conn)
    }

    fun setCookiesTxtContent(cookies: String) = viewModelScope.launch {
        settings.setCookiesTxtContent(cookies)
    }

    // â”€â”€ Privacy & storage delegations â”€â”€

    fun setVaultPinHash(hash: String) = viewModelScope.launch {
        settings.setVaultPinHash(hash)
    }

    fun setVaultBiometricEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setVaultBiometricEnabled(enabled)
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = calculateCacheSizeBytes(context)
            _cacheSize.value = formatFileSize(bytes)
        }
    }

    fun clearThumbnailCache(ctx: Context = context) {
        viewModelScope.launch(Dispatchers.IO) {
            val thumbDir = File(ctx.cacheDir, "thumbnails")
            if (thumbDir.exists()) {
                thumbDir.deleteRecursively()
            }
            val coilCache = File(ctx.cacheDir, "image_cache")
            if (coilCache.exists()) {
                coilCache.deleteRecursively()
            }
            val bytes = calculateCacheSizeBytes(ctx)
            _cacheSize.value = formatFileSize(bytes)
        }
    }

    suspend fun exportSettings(ctx: Context = context): String {
        return withContext(Dispatchers.IO) {
            settings.exportSettingsJson()
        }
    }

    fun importSettings(json: String, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                settings.importSettingsJson(json)
                onResult?.invoke(true)
            } catch (e: Exception) {
                onResult?.invoke(false)
            }
        }
    }

    fun resetAiDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = GalleryDatabase.getInstance(context)
                db.clearAllTables()
            } catch (_: Exception) {
            }
            GalleryIndexWorker.enqueueOneTime(context)
        }
    }

    fun verifyPin(input: String, hash: String): Boolean {
        if (hash.isBlank()) return false
        return hashPin(input).equals(hash, ignoreCase = true)
    }

    fun hashPin(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// MAIN SCREEN COMPOSABLE
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val appSettings by viewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val cacheSizeText by viewModel.cacheSize.collectAsStateWithLifecycle()
    val isIndexing by viewModel.isIndexingFlow.collectAsStateWithLifecycle(initialValue = false)

    // Dialog state variables
    var showReclusterDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetAiDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }

    var showAudioLangDialog by remember { mutableStateOf(false) }
    var showSubtitle1LangDialog by remember { mutableStateOf(false) }
    var showSubtitle2LangDialog by remember { mutableStateOf(false) }

    // SAF Activity Result Launchers
    val excludedFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = it.path ?: it.toString()
            viewModel.addExcludedFolder(path, appSettings.excludedFoldersJson)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Added excluded folder: $path")
            }
        }
    }

    val subtitleDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = it.path ?: it.toString()
            viewModel.setSubtitleSaveDir(path)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Subtitle save folder updated")
            }
        }
    }

    val exportJsonFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        OutputStreamWriter(stream).use { writer ->
                            writer.write(exportedJsonText)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Settings exported to file")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Export failed: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    val importJsonFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(it)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: ""
                    withContext(Dispatchers.Main) {
                        viewModel.importSettings(json) { success ->
                            coroutineScope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar("Settings imported successfully!")
                                } else {
                                    snackbarHostState.showSnackbar("Failed to import settings: Invalid format")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Import error: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    val cookieFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(it)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: ""
                    withContext(Dispatchers.Main) {
                        if (content.isNotBlank()) {
                            viewModel.setCookiesTxtContent(content)
                            snackbarHostState.showSnackbar("cookies.txt imported successfully")
                        } else {
                            snackbarHostState.showSnackbar("Empty cookies file")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Error importing cookies: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // Indexing status banner
            if (isIndexing) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Indexing gallery in background...",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // â”â” SECTION 1: General & Appearance â”â”
            item { SectionHeader("General & Appearance") }

            item {
                ListPreference(
                    title = "Theme Mode",
                    summary = when (appSettings.themeMode) {
                        "LIGHT" -> "Light"
                        "DARK" -> "Dark"
                        "AMOLED" -> "Pure AMOLED Black"
                        else -> "System Default"
                    },
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    options = listOf(
                        "SYSTEM" to "System Default",
                        "LIGHT" to "Light",
                        "DARK" to "Dark",
                        "AMOLED" to "Pure AMOLED Black"
                    ),
                    selected = appSettings.themeMode,
                    onSelected = { viewModel.setThemeMode(it) }
                )
            }

            item {
                SwitchPreference(
                    title = "Dynamic Material You Colors",
                    summary = "Derive theme accent colors from system wallpaper (Android 12+)",
                    checked = appSettings.dynamicColor,
                    icon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }

            item {
                SliderPreference(
                    title = "Gallery Grid Columns",
                    summary = "Number of columns displayed in the media grid",
                    value = appSettings.gridColumns.toFloat(),
                    valueRange = 2f..6f,
                    steps = 3,
                    valueLabel = { "${it.toInt()} columns" },
                    onValueChange = { viewModel.setGridColumns(it.toInt()) }
                )
            }

            item {
                ListPreference(
                    title = "Thumbnail Crop Mode",
                    summary = if (appSettings.thumbnailCropSquare) "Square (1:1)" else "Original Aspect Ratio",
                    options = listOf(
                        "true" to "Square (1:1)",
                        "false" to "Original Aspect Ratio"
                    ),
                    selected = appSettings.thumbnailCropSquare.toString(),
                    onSelected = { viewModel.setThumbnailCropSquare(it.toBoolean()) }
                )
            }

            item {
                SwitchPreference(
                    title = "Auto-Play GIFs",
                    summary = "Animate GIF and WebP images inside the gallery grid",
                    checked = appSettings.autoPlayGifs,
                    onCheckedChange = { viewModel.setAutoPlayGifs(it) }
                )
            }

            item {
                ListPreference(
                    title = "Default Startup Tab",
                    summary = when (appSettings.defaultStartupTab) {
                        "ALBUMS" -> "Albums"
                        "CLEANER" -> "Cleaner"
                        "PLAYER" -> "Player / Browser"
                        else -> "Timeline"
                    },
                    options = listOf(
                        "TIMELINE" to "Timeline",
                        "ALBUMS" to "Albums",
                        "CLEANER" to "Cleaner",
                        "PLAYER" to "Player / Browser"
                    ),
                    selected = appSettings.defaultStartupTab,
                    onSelected = { viewModel.setDefaultStartupTab(it) }
                )
            }

            // â”â” SECTION 2: AI & Smart Indexing â”â”
            item { SectionHeader("AI & Smart Indexing") }

            item {
                SliderPreference(
                    title = "Face Clustering Threshold",
                    summary = "Strictness of cosine similarity for grouping faces (Lower = stricter)",
                    value = appSettings.faceClusterThreshold,
                    valueRange = 0.65f..0.85f,
                    steps = 19,
                    valueLabel = { "%.2f".format(it) },
                    onValueChange = { viewModel.setFaceClusterThreshold(it) },
                    onValueChangeFinished = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Will re-cluster on next scan")
                        }
                    }
                )
            }

            item {
                ActionPreference(
                    title = "Re-Cluster Faces Now",
                    summary = "Recompute face embeddings and re-group people in the gallery",
                    buttonLabel = if (isIndexing) "Indexing..." else "Re-Cluster",
                    enabled = !isIndexing,
                    icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                    onClick = { showReclusterDialog = true }
                )
            }

            item {
                SliderPreference(
                    title = "OCR Min Characters",
                    summary = "Ignore text blocks with fewer characters during indexing",
                    value = appSettings.ocrMinChars.toFloat(),
                    valueRange = 30f..150f,
                    steps = 11,
                    valueLabel = { "${it.toInt()} chars" },
                    onValueChange = { viewModel.setOcrMinChars(it.toInt()) }
                )
            }

            item {
                SliderPreference(
                    title = "Duplicate Folder Similarity",
                    summary = "Jaccard threshold for flagging duplicate and burst folders",
                    value = appSettings.duplicateJaccardThreshold,
                    valueRange = 0.60f..1.00f,
                    steps = 8,
                    valueLabel = { "${(it * 100).toInt()}%" },
                    onValueChange = { viewModel.setDuplicateJaccardThreshold(it) }
                )
            }

            item {
                ListPreference(
                    title = "Background Scan Schedule",
                    summary = when (appSettings.bgScanSchedule) {
                        "CHARGING" -> "Only When Charging & Idle"
                        "MANUAL" -> "Manual Only"
                        else -> "Real-time (On media change)"
                    },
                    options = listOf(
                        "REALTIME" to "Real-time (On media change)",
                        "CHARGING" to "Only When Charging & Idle",
                        "MANUAL" to "Manual Only"
                    ),
                    selected = appSettings.bgScanSchedule,
                    onSelected = { viewModel.setBgScanSchedule(it) }
                )
            }

            item {
                ExcludedFoldersPreference(
                    foldersJson = appSettings.excludedFoldersJson,
                    onAddFolderClick = { excludedFolderPickerLauncher.launch(null) },
                    onRemoveFolderClick = { path ->
                        viewModel.removeExcludedFolder(path, appSettings.excludedFoldersJson)
                    }
                )
            }

            // â”â” SECTION 3: Video & Audio Player â”â”
            item { SectionHeader("Video & Audio Player") }

            item {
                ListPreference(
                    title = "Default Decoder Priority",
                    summary = when (appSettings.decoderPriority) {
                        "HW" -> "Hardware First"
                        "SW" -> "Software First"
                        else -> "Auto (Hardware with Software fallback)"
                    },
                    icon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = null) },
                    options = listOf(
                        "AUTO" to "Auto (Hardware with Software fallback)",
                        "HW" to "Hardware First",
                        "SW" to "Software First"
                    ),
                    selected = appSettings.decoderPriority,
                    onSelected = { viewModel.setDecoderPriority(it) }
                )
            }

            item {
                ListPreference(
                    title = "Resume Playback",
                    summary = when (appSettings.resumeBehavior) {
                        "ASK" -> "Always Ask"
                        "START_BEGINNING" -> "Start from Beginning"
                        else -> "Always Resume"
                    },
                    options = listOf(
                        "ALWAYS_RESUME" to "Always Resume",
                        "ASK" to "Always Ask",
                        "START_BEGINNING" to "Start from Beginning"
                    ),
                    selected = appSettings.resumeBehavior,
                    onSelected = { viewModel.setResumeBehavior(it) }
                )
            }

            item {
                ListPreference(
                    title = "Double-Tap Seek Duration",
                    summary = "${appSettings.doubleTapSeekSecs} seconds",
                    options = listOf(
                        "5" to "5 seconds",
                        "10" to "10 seconds",
                        "15" to "15 seconds",
                        "30" to "30 seconds"
                    ),
                    selected = appSettings.doubleTapSeekSecs.toString(),
                    onSelected = { viewModel.setDoubleTapSeekSecs(it.toInt()) }
                )
            }

            item {
                ListPreference(
                    title = "Long-Press Speed Boost",
                    summary = "%.1fx Speed".format(appSettings.longPressSpeed),
                    options = listOf(
                        "1.5" to "1.5x Speed",
                        "2.0" to "2.0x Speed",
                        "3.0" to "3.0x Speed"
                    ),
                    selected = appSettings.longPressSpeed.toString(),
                    onSelected = { viewModel.setLongPressSpeed(it.toFloat()) }
                )
            }

            item {
                SwitchPreference(
                    title = "Remember Brightness",
                    summary = "Save and restore custom brightness per video",
                    checked = appSettings.rememberBrightness,
                    onCheckedChange = { viewModel.setRememberBrightness(it) }
                )
            }

            item {
                SwitchPreference(
                    title = "Remember Playback Speed",
                    summary = "Keep selected speed across video sessions",
                    checked = appSettings.rememberSpeed,
                    onCheckedChange = { viewModel.setRememberSpeed(it) }
                )
            }

            item {
                SwitchPreference(
                    title = "Remember Audio Boost",
                    summary = "Preserve volume boost level (up to 200%)",
                    checked = appSettings.rememberAudioBoost,
                    onCheckedChange = { viewModel.setRememberAudioBoost(it) }
                )
            }

            item {
                SwitchPreference(
                    title = "Auto PiP on Home Press",
                    summary = "Switch to Picture-in-Picture when minimizing the app",
                    checked = appSettings.autoPipOnHome,
                    onCheckedChange = { viewModel.setAutoPipOnHome(it) }
                )
            }

            item {
                SwitchPreference(
                    title = "Background Audio on Screen Off",
                    summary = "Keep playing audio track when screen turns off",
                    checked = appSettings.backgroundAudioOnScreenOff,
                    onCheckedChange = { viewModel.setBackgroundAudioOnScreenOff(it) }
                )
            }

            item {
                ListPreference(
                    title = "Playlist End Action",
                    summary = when (appSettings.playlistEndAction) {
                        "LOOP_CURRENT" -> "Loop Current Video"
                        "LOOP_ALL" -> "Loop All Playlist"
                        "STOP" -> "Stop Playback"
                        else -> "Play Next Item"
                    },
                    options = listOf(
                        "PLAY_NEXT" to "Play Next Item",
                        "LOOP_CURRENT" to "Loop Current Video",
                        "LOOP_ALL" to "Loop All Playlist",
                        "STOP" to "Stop Playback"
                    ),
                    selected = appSettings.playlistEndAction,
                    onSelected = { viewModel.setPlaylistEndAction(it) }
                )
            }

            // â”â” SECTION 4: Subtitle & Audio Language â”â”
            item { SectionHeader("Subtitle & Audio Language") }

            item {
                PreferenceRow(
                    title = "Preferred Audio Language",
                    summary = getLanguageDisplayName(appSettings.preferredAudioLang),
                    icon = { Icon(Icons.Default.Subtitles, contentDescription = null) },
                    onClick = { showAudioLangDialog = true }
                )
            }

            item {
                PreferenceRow(
                    title = "Primary Subtitle Language",
                    summary = getLanguageDisplayName(appSettings.preferredSubtitleLang1),
                    icon = { Icon(Icons.Default.Subtitles, contentDescription = null) },
                    onClick = { showSubtitle1LangDialog = true }
                )
            }

            item {
                PreferenceRow(
                    title = "Secondary Subtitle Language",
                    summary = if (appSettings.preferredSubtitleLang2 == "NONE") "None (Disabled)" else getLanguageDisplayName(appSettings.preferredSubtitleLang2),
                    icon = { Icon(Icons.Default.Subtitles, contentDescription = null) },
                    onClick = { showSubtitle2LangDialog = true }
                )
            }

            item {
                SwitchPreference(
                    title = "Auto-Enable Subtitles (Foreign Audio)",
                    summary = "Automatically show subtitles when audio language is foreign",
                    checked = appSettings.autoSubtitleForeign,
                    onCheckedChange = { viewModel.setAutoSubtitleForeign(it) }
                )
            }

            item {
                SliderPreference(
                    title = "Default Subtitle Font Size",
                    summary = "Size of subtitle characters in player",
                    value = appSettings.subtitleFontSize.toFloat(),
                    valueRange = 10f..42f,
                    steps = 31,
                    valueLabel = { "${it.toInt()} sp" },
                    onValueChange = { viewModel.setSubtitleFontSize(it.toInt()) }
                )
            }

            item {
                ColorPickerRow(
                    title = "Default Text Color",
                    colors = SUBTITLE_PRESET_COLORS,
                    selectedColor = appSettings.subtitleColorArgb,
                    onColorSelected = { viewModel.setSubtitleColorArgb(it) }
                )
            }

            item {
                SliderPreference(
                    title = "Default Background Opacity",
                    summary = "Shading intensity behind subtitle text",
                    value = appSettings.subtitleBgOpacity,
                    valueRange = 0.0f..1.0f,
                    steps = 9,
                    valueLabel = { "${(it * 100).toInt()}%" },
                    onValueChange = { viewModel.setSubtitleBgOpacity(it) }
                )
            }

            item {
                ListPreference(
                    title = "Default Encoding",
                    summary = appSettings.subtitleEncoding,
                    options = listOf(
                        "UTF-8" to "UTF-8",
                        "UTF-16" to "UTF-16",
                        "Windows-1252" to "Windows-1252 (Western)",
                        "ISO-8859-1" to "ISO-8859-1 (Latin-1)",
                        "Shift-JIS" to "Shift-JIS (Japanese)",
                        "Big5" to "Big5 (Traditional Chinese)",
                        "GB18030" to "GB18030 (Simplified Chinese)"
                    ),
                    selected = appSettings.subtitleEncoding,
                    onSelected = { viewModel.setSubtitleEncoding(it) }
                )
            }

            item {
                PreferenceRow(
                    title = "External Subtitle Save Directory",
                    summary = if (appSettings.subtitleSaveDir.isNotBlank()) appSettings.subtitleSaveDir else "Default (App Internal Storage)",
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    trailingContent = {
                        Row {
                            if (appSettings.subtitleSaveDir.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSubtitleSaveDir("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(onClick = { subtitleDirPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.Folder, contentDescription = "Browse")
                            }
                        }
                    },
                    onClick = { subtitleDirPickerLauncher.launch(null) }
                )
            }

            // Downloader & Network Privacy
            item { SectionHeader("Downloader & Network Privacy") }

            item {
                SwitchPreference(
                    title = "Strict Offline Lockdown",
                    summary = "Completely isolates app from network; hard-blocks all downloading sockets",
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    checked = appSettings.strictOfflineLockdown,
                    onCheckedChange = { viewModel.setStrictOfflineLockdown(it) }
                )
            }

            item {
                SliderPreference(
                    title = "Aria2c Download Connections",
                    summary = "${appSettings.downloadAria2cConnections} parallel connections per task",
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    value = appSettings.downloadAria2cConnections.toFloat(),
                    valueRange = 1f..16f,
                    steps = 14,
                    valueLabel = { "${it.toInt()} conns" },
                    onValueChange = { viewModel.setDownloadAria2cConnections(it.toInt()) }
                )
            }

            item {
                ActionPreference(
                    title = "Import cookies.txt",
                    summary = if (appSettings.cookiesTxtContent.isNotBlank()) "Authentication cookies active (${appSettings.cookiesTxtContent.length} bytes)" else "Load cookies.txt for YouTube & Instagram authentication",
                    buttonLabel = if (appSettings.cookiesTxtContent.isNotBlank()) "Replace" else "Import",
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    onClick = { cookieFilePickerLauncher.launch(arrayOf("text/*", "*/*")) }
                )
            }

            if (appSettings.cookiesTxtContent.isNotBlank()) {
                item {
                    ActionPreference(
                        title = "Clear Cookies",
                        summary = "Remove stored authentication cookies",
                        buttonLabel = "Clear",
                        isDestructive = true,
                        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            viewModel.setCookiesTxtContent("")
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Authentication cookies removed")
                            }
                        }
                    )
                }
            }

            // â”â” SECTION 5: Privacy, Storage & Maintenance â”â”
            item { SectionHeader("Privacy, Storage & Maintenance") }

            item {
                VaultSecurityPreference(
                    isPinConfigured = appSettings.vaultPinHash.isNotBlank(),
                    isBiometricEnabled = appSettings.vaultBiometricEnabled,
                    onSetupPinClick = { showPinSetupDialog = true },
                    onToggleBiometric = { enableRequested ->
                        if (enableRequested) {
                            authenticateBiometric(
                                context = context,
                                onSuccess = {
                                    viewModel.setVaultBiometricEnabled(true)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Biometric lock enabled")
                                    }
                                },
                                onError = { msg ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                }
                            )
                        } else {
                            viewModel.setVaultBiometricEnabled(false)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Biometric lock disabled")
                            }
                        }
                    }
                )
            }

            item {
                ActionPreference(
                    title = "Clear Thumbnail Cache",
                    summary = "Cached image and video thumbnails ($cacheSizeText)",
                    buttonLabel = "Clear",
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    onClick = { showClearCacheDialog = true }
                )
            }

            item {
                ActionPreference(
                    title = "Export Settings JSON",
                    summary = "Save your configurations to clipboard or a backup file",
                    buttonLabel = "Export",
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    onClick = {
                        coroutineScope.launch {
                            exportedJsonText = viewModel.exportSettings(context)
                            showExportDialog = true
                        }
                    }
                )
            }

            item {
                ActionPreference(
                    title = "Import Settings JSON",
                    summary = "Restore settings from a saved JSON configuration",
                    buttonLabel = "Import",
                    icon = { Icon(Icons.Default.Restore, contentDescription = null) },
                    onClick = {
                        importJsonFileLauncher.launch(arrayOf("application/json", "text/*"))
                    }
                )
            }

            item {
                ActionPreference(
                    title = "Reset AI Database",
                    summary = "Erase face clusters, OCR index, and duplicate history; re-scans media",
                    buttonLabel = "Reset DB",
                    isDestructive = true,
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showResetAiDialog = true }
                )
            }

            // About footer
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SmartLocalGallery v1.0.0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Local-first AI Gallery & Pro Media Player",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // DIALOGS
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    // Re-cluster confirmation dialog
    if (showReclusterDialog) {
        AlertDialog(
            onDismissRequest = { showReclusterDialog = false },
            icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
            title = { Text("Re-Cluster Faces?") },
            text = {
                Text("This will recompute face embeddings with threshold %.2f and re-organize all person clusters.".format(appSettings.faceClusterThreshold))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReclusterDialog = false
                        viewModel.reclusterFaces()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Face re-clustering scheduled")
                        }
                    }
                ) {
                    Text("Re-Cluster Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReclusterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Cache confirmation dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Default.Storage, contentDescription = null) },
            title = { Text("Clear Thumbnail Cache?") },
            text = {
                Text("This will delete all cached thumbnails ($cacheSizeText). Thumbnails will be regenerated automatically as you browse.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearThumbnailCache(context)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Thumbnail cache cleared")
                        }
                    }
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset AI DB confirmation dialog
    if (showResetAiDialog) {
        AlertDialog(
            onDismissRequest = { showResetAiDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset AI Database?") },
            text = {
                Text("All detected face clusters, OCR search texts, and duplicate groupings will be wiped permanently. A clean gallery indexing scan will run in the background.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showResetAiDialog = false
                        viewModel.resetAiDatabase()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("AI database reset. Re-scan initiated.")
                        }
                    }
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAiDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // PIN Setup Dialog
    if (showPinSetupDialog) {
        PinSetupDialog(
            onConfirm = { pin ->
                val hash = viewModel.hashPin(pin)
                viewModel.setVaultPinHash(hash)
                showPinSetupDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Vault PIN configured successfully")
                }
            },
            onDismiss = { showPinSetupDialog = false }
        )
    }

    // Export Settings Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Settings JSON") },
            text = {
                Column {
                    Text(
                        text = "Your settings can be backed up as a JSON configuration:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(
                                    text = exportedJsonText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SmartLocalGallery Settings", exportedJsonText)
                            clipboard.setPrimaryClip(clip)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("JSON copied to clipboard")
                            }
                        }
                    ) {
                        Text("Copy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showExportDialog = false
                            exportJsonFileLauncher.launch("smart_gallery_settings.json")
                        }
                    ) {
                        Text("Save to File")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Audio Language Picker Dialog
    if (showAudioLangDialog) {
        LanguagePickerDialog(
            languages = ISO_LANGUAGES,
            selected = appSettings.preferredAudioLang,
            onSelected = {
                viewModel.setPreferredAudioLang(it)
                showAudioLangDialog = false
            },
            onDismiss = { showAudioLangDialog = false }
        )
    }

    // Primary Subtitle Language Picker Dialog
    if (showSubtitle1LangDialog) {
        LanguagePickerDialog(
            languages = ISO_LANGUAGES,
            selected = appSettings.preferredSubtitleLang1,
            onSelected = {
                viewModel.setPreferredSubtitleLang1(it)
                showSubtitle1LangDialog = false
            },
            onDismiss = { showSubtitle1LangDialog = false }
        )
    }

    // Secondary Subtitle Language Picker Dialog
    if (showSubtitle2LangDialog) {
        LanguagePickerDialog(
            languages = listOf("NONE" to "None (Disabled)") + ISO_LANGUAGES,
            selected = appSettings.preferredSubtitleLang2,
            onSelected = {
                viewModel.setPreferredSubtitleLang2(it)
                showSubtitle2LangDialog = false
            },
            onDismiss = { showSubtitle2LangDialog = false }
        )
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// REUSABLE PREFERENCE COMPOSABLES
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun SectionHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SliderPreference(
    title: String,
    summary: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueLabel: (Float) -> String = { "%.1f".format(it) },
    icon: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = valueLabel(value),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ListPreference(
    title: String,
    summary: String? = null,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    icon: @Composable (() -> Unit)? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = options, key = { it.first }) { (value, label) ->
                        val isSelected = (value == selected)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelected(value)
                                    showDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelected(value)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ActionPreference(
    title: String,
    summary: String? = null,
    buttonLabel: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    icon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isDestructive) {
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(buttonLabel)
            }
        } else {
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
fun ColorPickerRow(
    title: String,
    colors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            // Current color preview dot
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(selectedColor))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEach { colorArgb ->
                val isSelected = (colorArgb == selectedColor)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(colorArgb))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(colorArgb) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        val iconTint = if (Color(colorArgb).luminance() > 0.5f) Color.Black else Color.White
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PinSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Setup Vault PIN") },
        text = {
            Column {
                Text(
                    text = "Create a 4 to 8 digit numeric PIN to protect your hidden and locked files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            pin = it
                            errorMessage = null
                        }
                    },
                    label = { Text("Enter PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            confirmPin = it
                            errorMessage = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pin.length < 4) {
                                errorMessage = "PIN must be at least 4 digits"
                            } else if (pin != confirmPin) {
                                errorMessage = "PINs do not match"
                            } else {
                                onConfirm(pin)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length < 4) {
                        errorMessage = "PIN must be at least 4 digits"
                    } else if (pin != confirmPin) {
                        errorMessage = "PINs do not match"
                    } else {
                        onConfirm(pin)
                    }
                }
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LanguagePickerDialog(
    languages: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery, languages) {
        if (searchQuery.isBlank()) {
            languages
        } else {
            languages.filter { (code, name) ->
                name.contains(searchQuery, ignoreCase = true) || code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Language",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search language...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items = filteredLanguages, key = { it.first }) { (code, name) ->
                        val isSelected = (code == selected)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelected(code) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelected(code) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = code.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun PreferenceRow(
    title: String,
    summary: String? = null,
    icon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingContent()
        }
    }
}

@Composable
fun ExcludedFoldersPreference(
    foldersJson: String,
    onAddFolderClick: () -> Unit,
    onRemoveFolderClick: (String) -> Unit,
) {
    val folders = remember(foldersJson) { parseJsonFolderList(foldersJson) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Excluded Folders",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Folders ignored during media scanning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onAddFolderClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Folder", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (folders.isEmpty()) {
            Text(
                text = "No excluded folders. All storage directories are indexed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                folders.forEach { folderPath ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = folderPath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemoveFolderClick(folderPath) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultSecurityPreference(
    isPinConfigured: Boolean,
    isBiometricEnabled: Boolean,
    onSetupPinClick: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hidden & Locked Vault",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isPinConfigured) "PIN configured" else "No security PIN set",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPinConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSetupPinClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPinConfigured) "Change PIN" else "Setup PIN")
                }

                FilledTonalButton(
                    onClick = { onToggleBiometric(!isBiometricEnabled) },
                    enabled = isPinConfigured,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBiometricEnabled) "Biometric: ON" else "Enable Bio")
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// BIOMETRICS & UTILITY HELPERS
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private fun authenticateBiometric(
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val fragmentActivity = context.findFragmentActivity()
    if (fragmentActivity == null) {
        onError("Biometric authentication requires FragmentActivity")
        return
    }

    val biometricManager = BiometricManager.from(context)
    val canAuth = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Biometric hardware not available or not enrolled")
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            onError(errString.toString())
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            onError("Biometric verification failed")
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Authenticate Vault Access")
        .setSubtitle("Use your fingerprint or facial recognition")
        .setNegativeButtonText("Cancel")
        .build()

    val prompt = BiometricPrompt(fragmentActivity, executor, callback)
    prompt.authenticate(promptInfo)
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        if (ctx is ContextWrapper) ctx = ctx.baseContext else break
    }
    return null
}

private fun parseJsonFolderList(json: String): List<String> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun calculateCacheSizeBytes(context: Context): Long {
    var totalBytes = 0L
    val thumbDir = File(context.cacheDir, "thumbnails")
    if (thumbDir.exists()) {
        totalBytes += thumbDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
    val coilDir = File(context.cacheDir, "image_cache")
    if (coilDir.exists()) {
        totalBytes += coilDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
    return totalBytes
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val clampedGroup = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, clampedGroup.toDouble())
    return "%.1f %s".format(value, units[clampedGroup])
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

private fun getLanguageDisplayName(code: String): String {
    if (code.equals("SYSTEM", ignoreCase = true)) return "System Default"
    if (code.equals("NONE", ignoreCase = true)) return "None"
    return ISO_LANGUAGES.find { it.first.equals(code, ignoreCase = true) }?.second ?: code.uppercase()
}

// 8 preset subtitle colors: White, Yellow, Cyan, Green, Red, Orange, Blue, Pink
val SUBTITLE_PRESET_COLORS = listOf(
    0xFFFFFFFF.toInt(), // White
    0xFFFFEB3B.toInt(), // Yellow
    0xFF00E5FF.toInt(), // Cyan
    0xFF00E676.toInt(), // Green
    0xFFFF1744.toInt(), // Red
    0xFFFF9100.toInt(), // Orange
    0xFF2979FF.toInt(), // Blue
    0xFFFF4081.toInt(), // Pink
)

// Comprehensive ISO 639-1 language codes
val ISO_LANGUAGES = listOf(
    "SYSTEM" to "System Default",
    "en" to "English",
    "es" to "Spanish (EspaÃ±ol)",
    "fr" to "French (FranÃ§ais)",
    "de" to "German (Deutsch)",
    "it" to "Italian (Italiano)",
    "pt" to "Portuguese (PortuguÃªs)",
    "ru" to "Russian (Ð ÑƒÑÑÐºÐ¸Ð¹)",
    "zh" to "Chinese (ä¸­æ–‡)",
    "ja" to "Japanese (æ—¥æœ¬èªž)",
    "ko" to "Korean (í•œêµ­ì–´)",
    "ar" to "Arabic (Ø§Ù„Ø¹Ø±Ø¨ÙŠØ©)",
    "hi" to "Hindi (à¤¹à¤¿à¤¨à¥à¤¦à¥€)",
    "bn" to "Bengali (à¦¬à¦¾à¦‚à¦²à¦¾)",
    "nl" to "Dutch (Nederlands)",
    "pl" to "Polish (Polski)",
    "tr" to "Turkish (TÃ¼rkÃ§e)",
    "vi" to "Vietnamese (Tiáº¿ng Viá»‡t)",
    "th" to "Thai (à¹„à¸—à¸¢)",
    "id" to "Indonesian (Bahasa Indonesia)",
    "sv" to "Swedish (Svenska)",
    "da" to "Danish (Dansk)",
    "no" to "Norwegian (Norsk)",
    "fi" to "Finnish (Suomi)",
    "el" to "Greek (Î•Î»Î»Î·Î½Î¹ÎºÎ¬)",
    "cs" to "Czech (ÄŒeÅ¡tina)",
    "hu" to "Hungarian (Magyar)",
    "ro" to "Romanian (RomÃ¢nÄƒ)",
    "uk" to "Ukrainian (Ð£ÐºÑ€Ð°Ñ—Ð½ÑÑŒÐºÐ°)",
    "he" to "Hebrew (×¢×‘×¨×™×ª)",
    "fa" to "Persian (ÙØ§Ø±Ø³ÛŒ)",
    "ur" to "Urdu (Ø§Ø±Ø¯Ùˆ)",
    "ta" to "Tamil (à®¤à®®à®¿à®´à¯)",
    "te" to "Telugu (à°¤à±†à°²à±à°—à±)",
    "mr" to "Marathi (à¤®à¤°à¤¾à¤ à¥€)",
    "ms" to "Malay (Bahasa Melayu)",
    "fil" to "Filipino (Tagalog)"
)

