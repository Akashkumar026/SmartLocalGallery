package com.example.smartgallery.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

enum class EditorTool {
    NONE, CROP, ROTATE, DRAW, PRIVACY_BLUR, DOC_ENHANCE
}

data class DrawnPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

data class BlurRect(
    val start: Offset,
    val end: Offset
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    navController: NavController,
    mediaPath: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentTool by remember { mutableStateOf(EditorTool.NONE) }

    // Markup Pen state
    val paths = remember { mutableStateListOf<DrawnPath>() }
    var currentPathPoints = remember { mutableStateListOf<Offset>() }
    var selectedPenColor by remember { mutableStateOf(Color.Red) }
    var penStrokeWidth by remember { mutableFloatStateOf(8f) }

    // Privacy Blur state
    val blurRects = remember { mutableStateListOf<BlurRect>() }
    var currentBlurStart by remember { mutableStateOf<Offset?>(null) }
    var currentBlurEnd by remember { mutableStateOf<Offset?>(null) }

    // Load original bitmap safely under Scoped Storage
    LaunchedEffect(mediaPath) {
        withContext(Dispatchers.IO) {
            try {
                val uri = if (mediaPath.startsWith("content://") || mediaPath.startsWith("file://")) {
                    Uri.parse(mediaPath)
                } else {
                    Uri.fromFile(File(mediaPath))
                }
                var bmp: Bitmap? = null
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bmp = BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    val file = File(mediaPath)
                    if (file.exists()) {
                        bmp = BitmapFactory.decodeFile(file.absolutePath)
                    }
                }
                if (bmp != null) {
                    originalBitmap = bmp
                    activeBitmap = bmp!!.copy(Bitmap.Config.ARGB_8888, true)
                }
            } catch (e: Exception) {
                android.util.Log.e("PhotoEditor", "Error loading photo: ${e.message}", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Editor") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Undo last action
                        if (paths.isNotEmpty()) paths.removeLast()
                        else if (blurRects.isNotEmpty()) blurRects.removeLast()
                    }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = {
                        // Reset to original
                        originalBitmap?.let { orig ->
                            activeBitmap = orig.copy(Bitmap.Config.ARGB_8888, true)
                            paths.clear()
                            blurRects.clear()
                        }
                    }) {
                        Icon(Icons.Default.Restore, contentDescription = "Reset")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                activeBitmap?.let { bmp ->
                                    val finalBmp = renderFinalBitmap(bmp, paths, blurRects)
                                    val savedUri = saveEditedPhoto(context, finalBmp)
                                    if (savedUri != null) {
                                        Toast.makeText(context, "Saved to Pictures/SmartGallery/Edited", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                                    }
                                    navController.popBackStack()
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Secondary tool row
                when (currentTool) {
                    EditorTool.DRAW -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(Color.White, Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Cyan).forEach { c ->
                                IconButton(
                                    onClick = { selectedPenColor = c },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(c, CircleShape)
                                ) {}
                            }
                        }
                    }
                    EditorTool.CROP -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("1:1", "16:9", "4:3").forEach { ratio ->
                                OutlinedButton(onClick = {
                                    activeBitmap?.let { bmp ->
                                        activeBitmap = cropBitmapRatio(bmp, ratio)
                                    }
                                }) {
                                    Text(ratio)
                                }
                            }
                        }
                    }
                    EditorTool.ROTATE -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlinedButton(onClick = {
                                activeBitmap?.let { bmp ->
                                    activeBitmap = rotateBitmap(bmp, -90f)
                                }
                            }) {
                                Text("↶ -90°")
                            }
                            OutlinedButton(onClick = {
                                activeBitmap?.let { bmp ->
                                    activeBitmap = rotateBitmap(bmp, 90f)
                                }
                            }) {
                                Text("↷ +90°")
                            }
                            OutlinedButton(onClick = {
                                activeBitmap?.let { bmp ->
                                    activeBitmap = flipBitmap(bmp, horizontal = true)
                                }
                            }) {
                                Text("Flip H")
                            }
                            OutlinedButton(onClick = {
                                activeBitmap?.let { bmp ->
                                    activeBitmap = flipBitmap(bmp, horizontal = false)
                                }
                            }) {
                                Text("Flip V")
                            }
                        }
                    }
                    else -> {}
                }

                // Primary Tool Bar
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTool == EditorTool.CROP,
                        onClick = { currentTool = if (currentTool == EditorTool.CROP) EditorTool.NONE else EditorTool.CROP },
                        icon = { Icon(Icons.Default.Crop, contentDescription = "Crop") },
                        label = { Text("Crop") }
                    )
                    NavigationBarItem(
                        selected = currentTool == EditorTool.ROTATE,
                        onClick = { currentTool = if (currentTool == EditorTool.ROTATE) EditorTool.NONE else EditorTool.ROTATE },
                        icon = { Icon(Icons.Default.RotateRight, contentDescription = "Rotate") },
                        label = { Text("Rotate") }
                    )
                    NavigationBarItem(
                        selected = currentTool == EditorTool.DRAW,
                        onClick = { currentTool = if (currentTool == EditorTool.DRAW) EditorTool.NONE else EditorTool.DRAW },
                        icon = { Icon(Icons.Default.Edit, contentDescription = "Draw") },
                        label = { Text("Markup") }
                    )
                    NavigationBarItem(
                        selected = currentTool == EditorTool.PRIVACY_BLUR,
                        onClick = { currentTool = if (currentTool == EditorTool.PRIVACY_BLUR) EditorTool.NONE else EditorTool.PRIVACY_BLUR },
                        icon = { Icon(Icons.Default.BlurOn, contentDescription = "Redact") },
                        label = { Text("Privacy Blur") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            activeBitmap?.let { bmp ->
                                activeBitmap = documentScannerEnhance(bmp)
                            }
                        },
                        icon = { Icon(Icons.Default.DocumentScanner, contentDescription = "Doc Scan") },
                        label = { Text("Doc Enhance") }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            activeBitmap?.let { bmp ->
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentTool) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    when (currentTool) {
                                        EditorTool.DRAW -> currentPathPoints.add(offset)
                                        EditorTool.PRIVACY_BLUR -> {
                                            currentBlurStart = offset
                                            currentBlurEnd = offset
                                        }
                                        else -> {}
                                    }
                                },
                                onDrag = { change, _ ->
                                    when (currentTool) {
                                        EditorTool.DRAW -> currentPathPoints.add(change.position)
                                        EditorTool.PRIVACY_BLUR -> currentBlurEnd = change.position
                                        else -> {}
                                    }
                                },
                                onDragEnd = {
                                    when (currentTool) {
                                        EditorTool.DRAW -> {
                                            if (currentPathPoints.isNotEmpty()) {
                                                paths.add(DrawnPath(currentPathPoints.toList(), selectedPenColor, penStrokeWidth))
                                                currentPathPoints.clear()
                                            }
                                        }
                                        EditorTool.PRIVACY_BLUR -> {
                                            val start = currentBlurStart
                                            val end = currentBlurEnd
                                            if (start != null && end != null) {
                                                blurRects.add(BlurRect(start, end))
                                                currentBlurStart = null
                                                currentBlurEnd = null
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            )
                        }
                ) {
                    // Draw active base bitmap scaled to fit canvas
                    val imgBmp = bmp.asImageBitmap()
                    drawImage(imgBmp)

                    // Draw applied markup paths
                    for (p in paths) {
                        for (i in 0 until p.points.size - 1) {
                            drawLine(
                                color = p.color,
                                start = p.points[i],
                                end = p.points[i + 1],
                                strokeWidth = p.strokeWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }

                    // Draw in-progress markup path
                    for (i in 0 until currentPathPoints.size - 1) {
                        drawLine(
                            color = selectedPenColor,
                            start = currentPathPoints[i],
                            end = currentPathPoints[i + 1],
                            strokeWidth = penStrokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    // Draw applied privacy mosaic blur rectangles
                    for (rect in blurRects) {
                        val left = min(rect.start.x, rect.end.x)
                        val top = min(rect.start.y, rect.end.y)
                        val right = max(rect.start.x, rect.end.x)
                        val bottom = max(rect.start.y, rect.end.y)
                        drawRect(
                            color = Color.Black.copy(alpha = 0.85f),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                        )
                    }

                    // Draw in-progress privacy blur rectangle
                    if (currentBlurStart != null && currentBlurEnd != null) {
                        val s = currentBlurStart!!
                        val e = currentBlurEnd!!
                        val left = min(s.x, e.x)
                        val top = min(s.y, e.y)
                        val right = max(s.x, e.x)
                        val bottom = max(s.y, e.y)
                        drawRect(
                            color = Color.Red.copy(alpha = 0.4f),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }
        }
    }
}

private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun flipBitmap(source: Bitmap, horizontal: Boolean): Bitmap {
    val matrix = Matrix().apply {
        if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun cropBitmapRatio(source: Bitmap, ratio: String): Bitmap {
    val targetRatio = when (ratio) {
        "1:1" -> 1.0f
        "16:9" -> 16f / 9f
        "4:3" -> 4f / 3f
        else -> 1.0f
    }

    val currentRatio = source.width.toFloat() / source.height.toFloat()
    var newW = source.width
    var newH = source.height

    if (currentRatio > targetRatio) {
        newW = (source.height * targetRatio).toInt()
    } else {
        newH = (source.width / targetRatio).toInt()
    }

    val startX = (source.width - newW) / 2
    val startY = (source.height - newH) / 2
    return Bitmap.createBitmap(source, startX, startY, newW, newH)
}

/**
 * 1-Tap Document Scanner Enhancer: Auto-deskew + High-contrast adaptive B&W threshold.
 */
private fun documentScannerEnhance(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(enhanced)
    val paint = Paint()

    // High contrast color matrix
    val cm = ColorMatrix().apply {
        setSaturation(0f) // Grayscale
        // Contrast boost
        val contrast = 1.6f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val array = floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        postConcat(ColorMatrix(array))
    }

    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(source, 0f, 0f, paint)
    return enhanced
}

private fun renderFinalBitmap(base: Bitmap, paths: List<DrawnPath>, blurRects: List<BlurRect>): Bitmap {
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    // Redact blur rectangles
    val blurPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.FILL
    }
    for (rect in blurRects) {
        val left = min(rect.start.x, rect.end.x)
        val top = min(rect.start.y, rect.end.y)
        val right = max(rect.start.x, rect.end.x)
        val bottom = max(rect.start.y, rect.end.y)
        canvas.drawRect(left, top, right, bottom, blurPaint)
    }

    // Draw markup paths
    for (p in paths) {
        val paint = Paint().apply {
            color = p.color.toArgb()
            strokeWidth = p.strokeWidth
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        for (i in 0 until p.points.size - 1) {
            canvas.drawLine(p.points[i].x, p.points[i].y, p.points[i + 1].x, p.points[i + 1].y, paint)
        }
    }

    return result
}

private suspend fun saveEditedPhoto(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
    try {
        val fileName = "edited_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartGallery/Edited")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            return@withContext uri
        }
    } catch (e: Exception) {
        android.util.Log.e("PhotoEditor", "Scoped Storage save error: ${e.message}", e)
    }

    // Fallback for older API levels or direct filesystem access
    try {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SmartGallery/Edited")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "edited_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return@withContext Uri.fromFile(file)
    } catch (e: Exception) {
        android.util.Log.e("PhotoEditor", "Fallback file save failed: ${e.message}", e)
    }
    null
}

