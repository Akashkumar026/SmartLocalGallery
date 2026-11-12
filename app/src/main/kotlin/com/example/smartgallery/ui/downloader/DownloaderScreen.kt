package com.example.smartgallery.ui.downloader

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import com.example.smartgallery.data.db.DownloadTask
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.AppSettings
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.downloader.DownloadWorker
import com.example.smartgallery.downloader.MediaDownloaderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class DownloaderViewModel @Inject constructor(
    private val engine: MediaDownloaderEngine,
    private val db: GalleryDatabase,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    val downloadTasks: StateFlow<List<DownloadTask>> = db.downloadTaskDao().getAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    var urlInput by mutableStateOf("")
    var isInspecting by mutableStateOf(false)
    var mediaInfo by mutableStateOf<MediaDownloaderEngine.MediaInfoResult?>(null)

    // Format & Quality Pickers
    var selectedMediaType by mutableStateOf("VIDEO") // "VIDEO" or "AUDIO"
    var selectedVideoContainer by mutableStateOf("MP4") // "MP4" or "MKV"
    var selectedAudioContainer by mutableStateOf("MP3") // "MP3" or "M4A"
    var selectedResolution by mutableStateOf("1080p") // "4K (2160p)", "1440p", "1080p", "720p", "480p"
    var selectedAudioBitrate by mutableStateOf("320k") // "320k", "192k", "128k"

    var isClipMode by mutableStateOf(false)
    var clipStartTime by mutableStateOf("00:00:00")
    var clipEndTime by mutableStateOf("00:10:00")
    var errorMessage by mutableStateOf<String?>(null)

    fun inspectUrl(targetUrl: String = urlInput) {
        val cleanUrl = targetUrl.trim()
        if (cleanUrl.isBlank()) return
        urlInput = cleanUrl
        isInspecting = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val info = engine.fetchMediaInfo(cleanUrl)
                mediaInfo = info
                clipEndTime = MediaDownloaderEngine.formatDurationHms(info.durationMs)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to fetch media metadata"
            } finally {
                isInspecting = false
            }
        }
    }

    fun startDownload() {
        val info = mediaInfo ?: return
        viewModelScope.launch {
            val clipStartMs = if (isClipMode) MediaDownloaderEngine.parseHmsToMs(clipStartTime) else 0L
            val clipEndMs = if (isClipMode) MediaDownloaderEngine.parseHmsToMs(clipEndTime) else 0L

            val isAudio = selectedMediaType == "AUDIO"
            val qualityStr = if (isAudio) {
                "AUDIO_${selectedAudioBitrate}_${selectedAudioContainer}"
            } else {
                "${selectedResolution}_${selectedVideoContainer}"
            }

            val task = DownloadTask(
                url = info.url,
                title = info.title,
                mediaType = selectedMediaType,
                quality = qualityStr,
                status = "QUEUED",
                isClipped = isClipMode,
                clipStartMs = clipStartMs,
                clipEndMs = clipEndMs,
                createdAt = System.currentTimeMillis()
            )
            val taskId = db.downloadTaskDao().insert(task)

            // Enqueue WorkManager job with unique tag
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag("download_$taskId")
                .setInputData(Data.Builder().putLong(DownloadWorker.KEY_TASK_ID, taskId).build())
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)

            // Reset inspect form
            mediaInfo = null
            urlInput = ""
        }
    }

    fun pauseDownload(task: DownloadTask) {
        viewModelScope.launch {
            engine.cancelProcess(task.id)
            WorkManager.getInstance(context).cancelAllWorkByTag("download_${task.id}")
            db.downloadTaskDao().updateStatus(task.id, "PAUSED", null, null)
        }
    }

    fun saveCookies(content: String) {
        viewModelScope.launch {
            settings.setCookiesTxtContent(content)
        }
    }

    fun resumeDownload(task: DownloadTask) {
        viewModelScope.launch {
            db.downloadTaskDao().updateStatus(task.id, "QUEUED", null, null)
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag("download_${task.id}")
                .setInputData(Data.Builder().putLong(DownloadWorker.KEY_TASK_ID, task.id).build())
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    fun deleteTask(task: DownloadTask) {
        viewModelScope.launch {
            WorkManager.getInstance(context).cancelAllWorkByTag("download_${task.id}")
            db.downloadTaskDao().delete(task)
        }
    }
}

@Composable
fun DownloaderScreen(
    navController: NavController,
    viewModel: DownloaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appSettings by viewModel.appSettings.collectAsState()
    val tasks by viewModel.downloadTasks.collectAsState()

    // Clipboard auto-detection
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.startsWith("http://") || text.startsWith("https://")) {
                clipboardUrl = text
            }
        }
    }

    // Cookie import launcher
    val cookiePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""
                if (content.isNotBlank()) {
                    viewModel.saveCookies(content)
                    android.widget.Toast.makeText(context, "cookies.txt imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to import cookies: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal Downloader", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { cookiePickerLauncher.launch(arrayOf("text/*", "*/*")) }) {
                        Icon(Icons.Default.Key, contentDescription = "Import cookies.txt")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Strict Offline Lockdown Notice
            if (appSettings.strictOfflineLockdown) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Strict Offline Lockdown Active",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Network sockets are blocked. Disable lockdown in Settings to download media.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Clipboard Detection Banner
            clipboardUrl?.let { clipUrl ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Link detected in clipboard", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(clipUrl.take(45) + if (clipUrl.length > 45) "..." else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Button(onClick = {
                                viewModel.inspectUrl(clipUrl)
                                clipboardUrl = null
                            }) {
                                Text("Paste & Fetch")
                            }
                        }
                    }
                }
            }

            // URL Input Section
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Download Video, Reel, Post or Audio", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = viewModel.urlInput,
                            onValueChange = { viewModel.urlInput = it },
                            label = { Text("Paste YouTube, Instagram or Video URL") },
                            placeholder = { Text("https://www.youtube.com/watch?v=...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !appSettings.strictOfflineLockdown,
                            trailingIcon = {
                                if (viewModel.urlInput.isNotBlank()) {
                                    IconButton(onClick = { viewModel.urlInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                } else {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            viewModel.urlInput = clip.getItemAt(0).text?.toString() ?: ""
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.inspectUrl() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = viewModel.urlInput.isNotBlank() && !viewModel.isInspecting && !appSettings.strictOfflineLockdown
                        ) {
                            if (viewModel.isInspecting) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Analyzing Stream...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Fetch Media Info")
                            }
                        }
                    }
                }
            }

            // Error display
            viewModel.errorMessage?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Media Info & Options Card
            viewModel.mediaInfo?.let { info ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row {
                                if (info.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = info.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(90.dp, 60.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(info.title, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text("By: ${info.uploader}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Duration: ${MediaDownloaderEngine.formatDurationHms(info.durationMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("1. Media Type", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))

                            // Media Type Switch [ Video | Audio Only ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = viewModel.selectedMediaType == "VIDEO",
                                    onClick = { viewModel.selectedMediaType = "VIDEO" },
                                    label = { Text("Video & Audio") },
                                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                                FilterChip(
                                    selected = viewModel.selectedMediaType == "AUDIO",
                                    onClick = { viewModel.selectedMediaType = "AUDIO" },
                                    label = { Text("Audio Only") },
                                    leadingIcon = { Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            if (viewModel.selectedMediaType == "VIDEO") {
                                Text("2. Video Quality & Resolution", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("4K (2160p)", "1440p", "1080p", "720p", "480p").forEach { res ->
                                        FilterChip(
                                            selected = viewModel.selectedResolution.startsWith(res.take(4)),
                                            onClick = { viewModel.selectedResolution = res },
                                            label = { Text(res.split(" ").first(), fontSize = 12.sp) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Text("3. Container Format", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("MP4", "MKV").forEach { container ->
                                        FilterChip(
                                            selected = viewModel.selectedVideoContainer == container,
                                            onClick = { viewModel.selectedVideoContainer = container },
                                            label = { Text(container) }
                                        )
                                    }
                                }
                            } else {
                                Text("2. Audio Bitrate", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("320k" to "320 kbps (High)", "192k" to "192 kbps (Med)", "128k" to "128 kbps (Light)").forEach { (rate, label) ->
                                        FilterChip(
                                            selected = viewModel.selectedAudioBitrate == rate,
                                            onClick = { viewModel.selectedAudioBitrate = rate },
                                            label = { Text(label, fontSize = 12.sp) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Text("3. Audio Container", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("MP3", "M4A").forEach { container ->
                                        FilterChip(
                                            selected = viewModel.selectedAudioContainer == container,
                                            onClick = { viewModel.selectedAudioContainer = container },
                                            label = { Text(container) }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Timestamp Clipper
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("Download Specific Time Slice", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Clip precise start & end timestamps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = viewModel.isClipMode,
                                    onCheckedChange = { viewModel.isClipMode = it }
                                )
                            }

                            AnimatedVisibility(visible = viewModel.isClipMode) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = viewModel.clipStartTime,
                                            onValueChange = { viewModel.clipStartTime = it },
                                            label = { Text("Start (HH:MM:SS)") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = viewModel.clipEndTime,
                                            onValueChange = { viewModel.clipEndTime = it },
                                            label = { Text("End (HH:MM:SS)") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.startDownload() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (viewModel.isClipMode) "Download Time Slice" else "Start Download")
                            }
                        }
                    }
                }
            }

            // Active / Completed Downloads List
            if (tasks.isNotEmpty()) {
                item {
                    Text("Download Queue & History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                items(tasks, key = { it.id }) { task ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    val statusColor = when (task.status) {
                                        "COMPLETED" -> MaterialTheme.colorScheme.primary
                                        "DOWNLOADING" -> MaterialTheme.colorScheme.tertiary
                                        "PAUSED" -> MaterialTheme.colorScheme.secondary
                                        "FAILED" -> MaterialTheme.colorScheme.error
                                        else -> Color.Gray
                                    }
                                    Text(
                                        "Status: ${task.status} • ${task.quality}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (task.status) {
                                        "DOWNLOADING" -> {
                                            IconButton(onClick = { viewModel.pauseDownload(task) }) {
                                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                                            }
                                        }
                                        "PAUSED" -> {
                                            IconButton(onClick = { viewModel.resumeDownload(task) }) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                                            }
                                        }
                                        "FAILED" -> {
                                            IconButton(onClick = { viewModel.resumeDownload(task) }) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.error)
                                            }
                                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                                            }
                                        }
                                        "COMPLETED" -> {
                                            IconButton(onClick = {
                                                val path = if (task.outputPath.isNotBlank()) task.outputPath else task.url
                                                val encoded = URLEncoder.encode(path, StandardCharsets.UTF_8.name())
                                                navController.navigate("player/$encoded")
                                            }) {
                                                Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                                            }
                                        }
                                        else -> {
                                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                                            }
                                        }
                                    }
                                }
                            }

                            if (task.status == "DOWNLOADING") {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { task.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val spdText = if (task.speedBytesPerSec > 0) " • ${MediaDownloaderEngine.formatSpeed(task.speedBytesPerSec)}" else ""
                                    Text("${task.progress.toInt()}%$spdText", style = MaterialTheme.typography.bodySmall)
                                    if (task.etaSeconds > 0) {
                                        Text("ETA: ${task.etaSeconds}s", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            if (task.status == "FAILED" && !task.errorMsg.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = task.errorMsg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.resumeDownload(task) }) {
                                            Text("Retry", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
