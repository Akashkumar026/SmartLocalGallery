package com.example.smartgallery

import android.app.PictureInPictureParams
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Rational
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.exifinterface.media.ExifInterface
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.example.smartgallery.data.settings.AppSettings
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.player.ActivePlayerRegistry
import com.example.smartgallery.player.PlayerKeyEventDispatcher
import com.example.smartgallery.player.SmartVideoPlayerViewModel
import com.example.smartgallery.player.SubtitleAndAudioController
import com.example.smartgallery.ui.MainScreen
import com.example.smartgallery.ui.editor.PhotoEditorScreen
import com.example.smartgallery.ui.settings.SettingsScreen
import com.example.smartgallery.vault.VaultAndFileManager
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.nio.charset.Charset
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var keyEventDispatcher: PlayerKeyEventDispatcher

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var vaultManager: VaultAndFileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        val viewUri = if (intent?.action == Intent.ACTION_VIEW) {
            intent.data
        } else null

        setContent {
            val appSettings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )

            SmartLocalGalleryTheme(settings = appSettings) {
                val navController = rememberNavController()

                // Route shared link or viewed media
                LaunchedEffect(sharedText, viewUri) {
                    try {
                        if (sharedText != null) {
                            navController.navigate("main")
                        } else if (viewUri != null) {
                            val path = viewUri.path ?: viewUri.toString()
                            val encoded = URLEncoder.encode(path, "UTF-8")
                            if (intent?.type?.startsWith("video/") == true || intent?.type?.startsWith("audio/") == true) {
                                navController.navigate("player/$encoded")
                            } else if (intent?.type?.startsWith("image/") == true) {
                                navController.navigate("image_viewer/$encoded")
                            } else if (intent?.type == "application/pdf") {
                                navController.navigate("pdf_viewer/$encoded")
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "Failed to route external intent: ${t.message}", t)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(navController = navController)
                    }

                    composable(
                        route = "player/{encodedPath}",
                        arguments = listOf(navArgument("encodedPath") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedPath = backStackEntry.arguments?.getString("encodedPath") ?: ""
                        val decodedPath = Uri.decode(encodedPath)
                        com.example.smartgallery.player.SmartVideoPlayerScreen(
                            navController = navController,
                            mediaPath = decodedPath
                        )
                    }

                    composable("settings") {
                        SettingsScreen(navController = navController)
                    }

                    composable("vault") {
                        com.example.smartgallery.ui.vault.VaultScreen(navController = navController)
                    }

                    composable(
                        route = "photo_editor/{encodedPath}",
                        arguments = listOf(navArgument("encodedPath") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedPath = backStackEntry.arguments?.getString("encodedPath") ?: ""
                        val decodedPath = Uri.decode(encodedPath)
                        PhotoEditorScreen(
                            navController = navController,
                            mediaPath = decodedPath
                        )
                    }

                    composable(
                        route = "image_viewer/{encodedPath}",
                        arguments = listOf(navArgument("encodedPath") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedPath = backStackEntry.arguments?.getString("encodedPath") ?: ""
                        val decodedPath = Uri.decode(encodedPath)
                        ImageViewerScreen(
                            mediaPath = decodedPath,
                            navController = navController,
                            vaultManager = vaultManager
                        )
                    }

                    composable(
                        route = "pdf_viewer/{encodedPath}",
                        arguments = listOf(navArgument("encodedPath") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedPath = backStackEntry.arguments?.getString("encodedPath") ?: ""
                        val decodedPath = Uri.decode(encodedPath)
                        PdfViewerScreen(
                            mediaPath = decodedPath,
                            navController = navController
                        )
                    }

                    composable(
                        route = "text_viewer/{encodedPath}",
                        arguments = listOf(navArgument("encodedPath") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedPath = backStackEntry.arguments?.getString("encodedPath") ?: ""
                        val decodedPath = Uri.decode(encodedPath)
                        TextViewerScreen(
                            mediaPath = decodedPath,
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (ActivePlayerRegistry.isPlayerActive && event != null) {
            val p = ActivePlayerRegistry.activePlayer
            val vm = ActivePlayerRegistry.activeViewModel
            val sc = ActivePlayerRegistry.activeSubController
            if (p != null && vm != null && sc != null) {
                if (keyEventDispatcher.dispatch(keyCode, event, p, sc, vm)) {
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val autoPip = runBlocking { settingsRepository.settingsFlow.first().autoPipOnHome }
        if (autoPip && ActivePlayerRegistry.isPlayerActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ActivePlayerRegistry.pipModeFlow.value = isInPictureInPictureMode
    }
}

// ─── Fullscreen Image Viewer with Live OCR & QR Scanner ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
data class LiveOcrItem(
    val id: Int,
    val text: String,
    val leftFraction: Float,
    val topFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float
)

data class SmartBarcode(
    val rawValue: String,
    val isUrl: Boolean,
    val isWifi: Boolean,
    val wifiSsid: String = "",
    val wifiPass: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    mediaPath: String,
    navController: NavController,
    vaultManager: VaultAndFileManager
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageRotation by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 10f)
        offset += offsetChange
    }

    var isLiveOcrActive by remember { mutableStateOf(false) }
    val liveOcrItems = remember { mutableStateListOf<LiveOcrItem>() }
    val selectedOcrIds = remember { mutableStateListOf<Int>() }
    val detectedSmartBarcodes = remember { mutableStateListOf<SmartBarcode>() }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<com.example.smartgallery.data.db.MediaItem?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                pendingDeleteItem?.let { vaultManager.onItemsDeletedFromStorage(listOf(it)) }
                Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }
    }

    BackHandler(enabled = showDetailsSheet) {
        showDetailsSheet = false
    }
    BackHandler(enabled = selectedOcrIds.isNotEmpty()) {
        selectedOcrIds.clear()
    }
    BackHandler(enabled = isLiveOcrActive && selectedOcrIds.isEmpty()) {
        isLiveOcrActive = false
    }

    val file = remember(mediaPath) { File(mediaPath) }
    val mediaUri = remember(mediaPath) {
        if (mediaPath.startsWith("content://") || mediaPath.startsWith("file://")) {
            Uri.parse(mediaPath)
        } else {
            Uri.fromFile(file)
        }
    }

    fun analyzeImageForOcrAndBarcodes() {
        scope.launch {
            isAnalyzing = true
            liveOcrItems.clear()
            detectedSmartBarcodes.clear()
            selectedOcrIds.clear()

            withContext(Dispatchers.IO) {
                val bmp = try {
                    context.contentResolver.openInputStream(mediaUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    if (file.exists()) BitmapFactory.decodeFile(mediaPath) else null
                }

                if (bmp != null) {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val barcodeScanner = BarcodeScanning.getClient()
                    val inputImg = InputImage.fromBitmap(bmp, 0)

                    try {
                        val ocrResult = recognizer.process(inputImg).await()
                        val bWidth = bmp.width.toFloat().coerceAtLeast(1f)
                        val bHeight = bmp.height.toFloat().coerceAtLeast(1f)
                        var counter = 0

                        for (block in ocrResult.textBlocks) {
                            for (line in block.lines) {
                                val box = line.boundingBox
                                if (box != null) {
                                    val left = (box.left / bWidth).coerceIn(0f, 1f)
                                    val top = (box.top / bHeight).coerceIn(0f, 1f)
                                    val right = (box.right / bWidth).coerceIn(0f, 1f)
                                    val bottom = (box.bottom / bHeight).coerceIn(0f, 1f)
                                    liveOcrItems.add(
                                        LiveOcrItem(
                                            id = counter++,
                                            text = line.text,
                                            leftFraction = left,
                                            topFraction = top,
                                            widthFraction = (right - left).coerceAtLeast(0.01f),
                                            heightFraction = (bottom - top).coerceAtLeast(0.01f)
                                        )
                                    )
                                }
                            }
                        }

                        val barResult = barcodeScanner.process(inputImg).await()
                        for (barcode in barResult) {
                            val raw = barcode.rawValue ?: continue
                            val isUrl = raw.startsWith("http://", true) || raw.startsWith("https://", true)
                            val isWifi = raw.startsWith("WIFI:", true)
                            var ssid = ""
                            var pass = ""
                            if (isWifi) {
                                val sMatch = Regex("S:(.*?);").find(raw)
                                val pMatch = Regex("P:(.*?);").find(raw)
                                ssid = sMatch?.groupValues?.getOrNull(1) ?: ""
                                pass = pMatch?.groupValues?.getOrNull(1) ?: ""
                            }
                            detectedSmartBarcodes.add(
                                SmartBarcode(
                                    rawValue = raw,
                                    isUrl = isUrl,
                                    isWifi = isWifi,
                                    wifiSsid = ssid,
                                    wifiPass = pass
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        recognizer.close()
                        barcodeScanner.close()
                        bmp.recycle()
                    }
                }
            }
            isAnalyzing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Rotate
                    IconButton(onClick = {
                        imageRotation = (imageRotation + 90f) % 360f
                    }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate")
                    }
                    // Edit
                    IconButton(onClick = {
                        val encoded = URLEncoder.encode(mediaPath, "UTF-8")
                        navController.navigate("photo_editor/$encoded")
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    // Live OCR Lens Toggle
                    IconButton(onClick = {
                        isLiveOcrActive = !isLiveOcrActive
                        if (isLiveOcrActive && liveOcrItems.isEmpty() && !isAnalyzing) {
                            analyzeImageForOcrAndBarcodes()
                        }
                    }) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = "Live OCR Overlay",
                            tint = if (isLiveOcrActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    // Photo Details & EXIF Histogram
                    IconButton(onClick = { showDetailsSheet = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Details & EXIF")
                    }
                    // Privacy Scrubber & Share
                    IconButton(onClick = {
                        scope.launch {
                            val cleanFile = vaultManager.stripGpsAndExif(mediaPath)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cleanFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Sanitized Photo"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Sanitize & Share")
                    }
                    // Delete Photo
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Photo",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1.5f) 1f else 3f
                            offset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boxWidth = maxWidth
                val boxHeight = maxHeight

                AsyncImage(
                    model = mediaUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                            rotationZ = imageRotation
                        }
                        .transformable(state = transformState),
                    contentScale = ContentScale.Fit
                )

                // Live Interactive OCR Overlay
                if (isLiveOcrActive && liveOcrItems.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                                rotationZ = imageRotation
                            }
                    ) {
                        liveOcrItems.forEach { item ->
                            val isSelected = selectedOcrIds.contains(item.id)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(item.widthFraction)
                                    .fillMaxHeight(item.heightFraction)
                                    .offset(
                                        x = boxWidth * item.leftFraction,
                                        y = boxHeight * item.topFraction
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        else Color.Cyan.copy(alpha = 0.20f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Cyan.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        if (isSelected) selectedOcrIds.remove(item.id)
                                        else selectedOcrIds.add(item.id)
                                    }
                            )
                        }
                    }
                }
            }

            if (isAnalyzing) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("Reading Live Text & Codes...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Floating Smart Barcode Card
            if (detectedSmartBarcodes.isNotEmpty()) {
                val barcode = detectedSmartBarcodes.first()
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = if (isLiveOcrActive) 76.dp else 24.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (barcode.isWifi) "WiFi Network: ${barcode.wifiSsid}" else if (barcode.isUrl) "Web Link" else "Scanned Barcode",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                if (barcode.isWifi) "Password: ${barcode.wifiPass}" else barcode.rawValue,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (barcode.isUrl) {
                                FilledTonalButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(barcode.rawValue)))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Open")
                                }
                            }
                            Button(
                                onClick = {
                                    val textToCopy = if (barcode.isWifi) barcode.wifiPass else barcode.rawValue
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Copy")
                            }
                        }
                    }
                }
            }

            // Floating Live OCR Action Bar
            if (isLiveOcrActive && liveOcrItems.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val selectedText = if (selectedOcrIds.isNotEmpty()) {
                                    liveOcrItems.filter { selectedOcrIds.contains(it.id) }.joinToString("\n") { it.text }
                                } else {
                                    liveOcrItems.joinToString("\n") { it.text }
                                }
                                clipboardManager.setText(AnnotatedString(selectedText))
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (selectedOcrIds.isNotEmpty()) "Copy (${selectedOcrIds.size})" else "Copy All")
                        }

                        OutlinedButton(
                            onClick = {
                                if (selectedOcrIds.size == liveOcrItems.size) selectedOcrIds.clear()
                                else {
                                    selectedOcrIds.clear()
                                    selectedOcrIds.addAll(liveOcrItems.map { it.id })
                                }
                            }
                        ) {
                            Text(if (selectedOcrIds.size == liveOcrItems.size) "Deselect" else "Select All")
                        }

                        IconButton(
                            onClick = {
                                val textToShare = if (selectedOcrIds.isNotEmpty()) {
                                    liveOcrItems.filter { selectedOcrIds.contains(it.id) }.joinToString("\n") { it.text }
                                } else {
                                    liveOcrItems.joinToString("\n") { it.text }
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Extracted Text"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            }
        }

        // Deep EXIF & RGB Histogram Sheet
        if (showDetailsSheet) {
            PhotoDetailsBottomSheet(
                mediaPath = mediaPath,
                mediaUri = mediaUri,
                onDismiss = { showDetailsSheet = false }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete Photo?") },
                text = { Text("Are you sure you want to delete this photo from your device? This cannot be undone.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            showDeleteDialog = false
                            scope.launch {
                                val item = vaultManager.findMediaItem(mediaPath)
                                if (item != null) {
                                    pendingDeleteItem = item
                                    when (val res = vaultManager.requestDeleteMedia(listOf(item))) {
                                        is VaultAndFileManager.DeleteResult.RequiresUserConsent -> {
                                            deleteLauncher.launch(
                                                IntentSenderRequest.Builder(res.intentSender).build()
                                            )
                                        }
                                        is VaultAndFileManager.DeleteResult.Success -> {
                                            vaultManager.onItemsDeletedFromStorage(listOf(item))
                                            Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                                            navController.popBackStack()
                                        }
                                        is VaultAndFileManager.DeleteResult.Error -> {
                                            Toast.makeText(context, "Could not delete: ${res.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    val f = File(mediaPath)
                                    if (f.exists()) f.delete()
                                    Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailsBottomSheet(
    mediaPath: String,
    mediaUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val rHistogram = remember { IntArray(256) }
    val gHistogram = remember { IntArray(256) }
    val bHistogram = remember { IntArray(256) }
    var maxHistCount by remember { mutableIntStateOf(1) }
    var exifData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var gpsCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var imageDimensions by remember { mutableStateOf("0 x 0") }
    var fileSizeText by remember { mutableStateOf("") }

    LaunchedEffect(mediaUri) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Scoped Storage EXIF reading
                context.contentResolver.openInputStream(mediaUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val map = mutableMapOf<String, String>()
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: ""
                    if (make.isNotBlank() || model.isNotBlank()) map["Camera"] = "$make $model".trim()
                    exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let { if (it.isNotBlank()) map["Lens"] = it }
                    exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { if (it.isNotBlank()) map["Focal Length"] = it }
                    exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { if (it.isNotBlank()) map["Aperture"] = "f/$it" }
                    exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { if (it.isNotBlank()) map["ISO"] = it }
                    exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { if (it.isNotBlank()) map["Shutter Speed"] = "${it}s" }
                    exif.getAttribute(ExifInterface.TAG_FLASH)?.let { if (it.isNotBlank()) map["Flash"] = it }
                    exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)?.let { if (it.isNotBlank()) map["White Balance"] = it }
                    val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                    val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                    if (width > 0 && height > 0) {
                        val mp = "%.1f MP".format((width * height) / 1_000_000f)
                        imageDimensions = "$width x $height ($mp)"
                    }
                    val latLong = exif.latLong
                    if (latLong != null && latLong.size >= 2) {
                        gpsCoords = Pair(latLong[0], latLong[1])
                    }
                    exifData = map
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Compute 256-Bin RGB Color Histogram from downsampled bitmap
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = context.contentResolver.openInputStream(mediaUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
                if (bmp != null) {
                    val w = bmp.width
                    val h = bmp.height
                    val pixels = IntArray(w * h)
                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                    for (pixel in pixels) {
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        rHistogram[r]++
                        gHistogram[g]++
                        bHistogram[b]++
                    }
                    var maxC = 1
                    for (i in 0..255) {
                        if (rHistogram[i] > maxC) maxC = rHistogram[i]
                        if (gHistogram[i] > maxC) maxC = gHistogram[i]
                        if (bHistogram[i] > maxC) maxC = bHistogram[i]
                    }
                    maxHistCount = maxC
                    bmp.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val f = File(mediaPath)
            if (f.exists()) {
                val mb = f.length() / (1024f * 1024f)
                fileSizeText = if (mb >= 1.0f) "%.2f MB".format(mb) else "${f.length() / 1024} KB"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Photo Details & EXIF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // 256-Bin RGB Color Histogram Canvas Graph
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("RGB Color Histogram (256 Bins)", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cWidth = size.width
                        val cHeight = size.height
                        val safeMax = maxHistCount.toFloat().coerceAtLeast(1f)

                        fun drawChannel(hist: IntArray, color: Color) {
                            val path = Path()
                            path.moveTo(0f, cHeight)
                            for (bin in 0..255) {
                                val x = (bin.toFloat() / 255f) * cWidth
                                val y = cHeight - (hist[bin].toFloat() / safeMax) * cHeight
                                path.lineTo(x, y)
                            }
                            path.lineTo(cWidth, cHeight)
                            path.close()
                            drawPath(path, color, style = Fill)
                            drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = 1.5f))
                        }

                        drawChannel(rHistogram, Color(0x66FF3333))
                        drawChannel(gHistogram, Color(0x6633FF33))
                        drawChannel(bHistogram, Color(0x663388FF))
                    }
                }
            }

            // Quick System Actions (Wallpaper & Contact Photo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val bmp = context.contentResolver.openInputStream(mediaUri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream)
                                }
                                if (bmp != null) {
                                    WallpaperManager.getInstance(context).setBitmap(bmp)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Wallpaper set successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                    bmp.recycle()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to set wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Wallpaper")
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val attachIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                setDataAndType(mediaUri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(attachIntent, "Set as Contact Photo"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not launch contact photo chooser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Contact")
                }
            }

            // Dimensions & Size Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("File Information", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    if (imageDimensions != "0 x 0") {
                        Text("Dimensions: $imageDimensions", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (fileSizeText.isNotBlank()) {
                        Text("Size: $fileSizeText", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Path: $mediaPath", style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2)
                }
            }

            // EXIF Card
            if (exifData.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Camera & Exposure", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        exifData.forEach { (label, value) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Location Card with Maps launcher
            gpsCoords?.let { (lat, lon) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("GPS Location", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            Text("%.5f, %.5f".format(lat, lon), style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(
                            onClick = {
                                val mapUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Photo Location)")
                                val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                                context.startActivity(mapIntent)
                            }
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open Maps")
                        }
                    }
                }
            }
        }
    }
}

// ─── Multi-Page Native PdfRenderer Viewer ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    mediaPath: String,
    navController: NavController
) {
    val file = remember(mediaPath) { File(mediaPath) }
    var pageCount by remember { mutableIntStateOf(0) }
    val pages = remember { mutableStateListOf<Bitmap>() }

    LaunchedEffect(mediaPath) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.RGB_565)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pages.add(bmp)
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${file.name} ($pageCount pages)") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.DarkGray)
        ) {
            itemsIndexed(pages) { index, bmp ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                        Text(
                            "Page ${index + 1} of $pageCount",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Text / Markdown / JSON Viewer ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    mediaPath: String,
    navController: NavController
) {
    val file = remember(mediaPath) { File(mediaPath) }
    var contentText by remember { mutableStateOf("") }

    LaunchedEffect(mediaPath) {
        withContext(Dispatchers.IO) {
            try {
                contentText = file.readText(Charset.forName("UTF-8"))
            } catch (e: Exception) {
                contentText = "Failed to load document content: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SelectionContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = contentText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ─── Smart Local Gallery Material Theme ─────────────────────────────────────

@Composable
fun SmartLocalGalleryTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (settings.themeMode) {
        "DARK", "AMOLED" -> true
        "LIGHT" -> false
        else -> isSystemDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> {
            if (settings.themeMode == "AMOLED") {
                darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color(0xFF121212)
                )
            } else {
                darkColorScheme()
            }
        }
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

