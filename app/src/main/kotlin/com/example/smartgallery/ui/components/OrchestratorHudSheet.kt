package com.example.smartgallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartgallery.engine.OrchestratorState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrchestratorHudSheet(
    state: OrchestratorState,
    onStartAll: () -> Unit,
    onCancelAll: () -> Unit,
    onStartDuplicates: () -> Unit,
    onTogglePauseDuplicates: () -> Unit,
    onCancelDuplicates: () -> Unit,
    onStartCategorizer: () -> Unit,
    onTogglePauseCategorizer: () -> Unit,
    onCancelCategorizer: () -> Unit,
    onStartFaces: () -> Unit,
    onTogglePauseFaces: () -> Unit,
    onCancelFaces: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Concurrent Multi-Task AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Parallel background engines (SupervisorScope)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isAnyRunning) {
                        OutlinedButton(onClick = onCancelAll) {
                            Text("Cancel All")
                        }
                    } else {
                        Button(onClick = onStartAll) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Start All")
                        }
                    }
                }
            }

            // Channel 1: Duplicates & Blur
            PipelineChannelCard(
                title = "1. Duplicates & Blur Engine",
                subtitle = "xxHash64 & Laplacian Variance (IO Parallelism: 2)",
                icon = Icons.Default.CleaningServices,
                progress = state.duplicates.progress,
                statusText = state.duplicates.statusText,
                throughput = if (state.duplicates.itemsPerSec > 0) String.format(Locale.ROOT, "%.1f items/s", state.duplicates.itemsPerSec) else "",
                isRunning = state.duplicates.isRunning,
                isPaused = state.duplicates.isPaused,
                onStart = onStartDuplicates,
                onTogglePause = onTogglePauseDuplicates,
                onCancel = onCancelDuplicates
            )

            // Channel 2: Smart Document & Image Categorizer
            PipelineChannelCard(
                title = "2. Smart Document & Image Categorizer",
                subtitle = "ML Kit OCR, Barcodes & Image Labeling (Parallelism: 2)",
                icon = Icons.Default.Category,
                progress = state.categorizer.progress,
                statusText = state.categorizer.statusText,
                throughput = if (state.categorizer.itemsPerSec > 0) String.format(Locale.ROOT, "%.1f items/s", state.categorizer.itemsPerSec) else "",
                isRunning = state.categorizer.isRunning,
                isPaused = state.categorizer.isPaused,
                onStart = onStartCategorizer,
                onTogglePause = onTogglePauseCategorizer,
                onCancel = onCancelCategorizer
            )

            // Channel 3: Face Clustering Engine
            PipelineChannelCard(
                title = "3. Face Clustering Engine",
                subtitle = "TFLite 192-d Embeddings & DBSCAN (Parallelism: 1)",
                icon = Icons.Default.Face,
                progress = state.faces.progress,
                statusText = state.faces.statusText,
                throughput = "",
                isRunning = state.faces.isRunning,
                isPaused = state.faces.isPaused,
                onStart = onStartFaces,
                onTogglePause = onTogglePauseFaces,
                onCancel = onCancelFaces
            )
        }
    }
}

@Composable
fun PipelineChannelCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    progress: Float,
    statusText: String,
    throughput: String,
    isRunning: Boolean,
    isPaused: Boolean,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        IconButton(onClick = onTogglePause) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    } else {
                        Button(
                            onClick = onStart,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Run", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (isRunning || progress > 0f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(statusText, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                    if (throughput.isNotBlank()) {
                        Text(throughput, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(statusText, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

