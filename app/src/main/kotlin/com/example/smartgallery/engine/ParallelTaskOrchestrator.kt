package com.example.smartgallery.engine

import android.os.Process
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.player.ActivePlayerRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class PipelineError(
    val itemName: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class PipelineProgress(
    val title: String,
    val progress: Float = 0f,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val itemsPerSec: Float = 0f,
    val statusText: String = "Idle",
    val errorCount: Int = 0,
    val errors: List<PipelineError> = emptyList()
)

@Immutable
data class OrchestratorState(
    val duplicates: PipelineProgress = PipelineProgress(title = "Duplicates & Blur"),
    val categorizer: PipelineProgress = PipelineProgress(title = "Smart Categorizer"),
    val faces: PipelineProgress = PipelineProgress(title = "Face Clustering"),
    val isAnyRunning: Boolean = false
)

@Singleton
class ParallelTaskOrchestrator @Inject constructor(
    private val db: GalleryDatabase,
    private val engine: LocalGalleryEngine,
    private val settings: SettingsRepository
) {
    companion object {
        private const val TAG = "ParallelTaskOrchestrator"
    }

    private val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(OrchestratorState())
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    private var jobDuplicates: Job? = null
    private var jobCategorizer: Job? = null
    private var jobFaces: Job? = null

    private var pauseDuplicates = false
    private var pauseCategorizer = false
    private var pauseFaces = false

    private suspend fun throttleIfActive() {
        while (ActivePlayerRegistry.isPlaying) {
            delay(1000)
        }
    }

    private fun updateAnyRunning() {
        val anyRunning = _state.value.duplicates.isRunning ||
                _state.value.categorizer.isRunning ||
                _state.value.faces.isRunning
        _state.update { it.copy(isAnyRunning = anyRunning) }
    }

    // ─── Pipeline 1: Duplicate & Blur Scanner ─────────────────────────────

    fun startDuplicatesPipeline() {
        if (jobDuplicates?.isActive == true) return
        pauseDuplicates = false
        jobDuplicates = orchestratorScope.launch(Dispatchers.IO.limitedParallelism(2)) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            _state.update {
                it.copy(
                    duplicates = it.duplicates.copy(
                        isRunning = true,
                        isPaused = false,
                        statusText = "Hashing & analyzing files..."
                    )
                )
            }
            updateAnyRunning()

            val startTime = System.currentTimeMillis()
            var processed = 0

            try {
                // 1. Compute xxHash for unhashed items
                val unhashed = db.mediaItemDao().getUnhashedItemsSync()
                val total = unhashed.size.coerceAtLeast(1)
                val errors = mutableListOf<PipelineError>()
                _state.update {
                    it.copy(
                        duplicates = it.duplicates.copy(
                            totalCount = total,
                            processedCount = 0,
                            progress = 0f,
                            errorCount = 0,
                            errors = emptyList()
                        )
                    )
                }

                for (item in unhashed) {
                    while (pauseDuplicates) delay(500)
                    throttleIfActive()

                    try {
                        engine.hashItem(item)
                    } catch (itemErr: Exception) {
                        errors.add(PipelineError(item.displayName, itemErr.message ?: "Hash calculation failure"))
                    }
                    processed++

                    if (processed % 5 == 0) {
                        yield()
                        val elapsedSecs = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.1f)
                        val rate = processed / elapsedSecs
                        _state.update {
                            it.copy(
                                duplicates = it.duplicates.copy(
                                    processedCount = processed,
                                    progress = (processed.toFloat() / total.toFloat()).coerceIn(0f, 0.7f),
                                    itemsPerSec = rate,
                                    statusText = "Hashing: $processed / $total",
                                    errorCount = errors.size,
                                    errors = errors.toList()
                                )
                            )
                        }
                    }
                }

                // 2. Exact duplicates
                _state.update {
                    it.copy(
                        duplicates = it.duplicates.copy(
                            progress = 0.75f,
                            statusText = "Detecting duplicate files..."
                        )
                    )
                }
                engine.findExactDuplicates()

                // 3. Duplicate folders
                _state.update {
                    it.copy(
                        duplicates = it.duplicates.copy(
                            progress = 0.85f,
                            statusText = "Detecting duplicate albums..."
                        )
                    )
                }
                engine.findDuplicateFolders()

                // 4. Blurry & burst photos
                _state.update {
                    it.copy(
                        duplicates = it.duplicates.copy(
                            progress = 0.95f,
                            statusText = "Scanning burst & blurry photos..."
                        )
                    )
                }
                engine.analyzeBurstPhotosAndBlur()

                _state.update {
                    it.copy(
                        duplicates = it.duplicates.copy(
                            isRunning = false,
                            isPaused = false,
                            progress = 1.0f,
                            statusText = if (errors.isEmpty()) "Completed" else "Done with ${errors.size} errors",
                            errorCount = errors.size,
                            errors = errors.toList()
                        )
                    )
                }
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(
                        duplicates = it.duplicates.copy(
                            isRunning = false,
                            isPaused = false,
                            statusText = "Cancelled"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Duplicate pipeline error: ${e.message}")
                _state.update {
                    it.copy(
                        duplicates = it.duplicates.copy(
                            isRunning = false,
                            statusText = "Error: ${e.message?.take(30)}"
                        )
                    )
                }
            } finally {
                updateAnyRunning()
            }
        }
    }

    // ─── Pipeline 2: Smart Document & Image Categorizer ───────────────────

    fun startCategorizerPipeline() {
        if (jobCategorizer?.isActive == true) return
        pauseCategorizer = false
        jobCategorizer = orchestratorScope.launch(Dispatchers.Default.limitedParallelism(2)) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            _state.update {
                it.copy(
                    categorizer = it.categorizer.copy(
                        isRunning = true,
                        isPaused = false,
                        statusText = "Classifying images...",
                        errorCount = 0,
                        errors = emptyList()
                    )
                )
            }
            updateAnyRunning()

            val startTime = System.currentTimeMillis()
            val errors = mutableListOf<PipelineError>()
            try {
                val checkpointId = settings.settingsFlow.first().lastScanCheckpointId
                val allImages = db.mediaItemDao().getAllSync().filter { it.mediaType == "IMAGE" && !it.isUserOverridden }
                val startIdx = if (checkpointId > 0) {
                    val idx = allImages.indexOfFirst { it.id == checkpointId }
                    if (idx >= 0) idx else 0
                } else 0

                val images = allImages.drop(startIdx)
                val total = allImages.size.coerceAtLeast(1)

                _state.update {
                    it.copy(
                        categorizer = it.categorizer.copy(
                            totalCount = total,
                            processedCount = startIdx,
                            progress = (startIdx.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        )
                    )
                }

                var processed = startIdx
                for (item in images) {
                    while (pauseCategorizer) delay(500)
                    throttleIfActive()

                    try {
                        engine.analyzeImageWithAllSignals(item)
                    } catch (itemErr: Exception) {
                        errors.add(PipelineError(item.displayName, itemErr.message ?: "Classification failure"))
                    }
                    processed++

                    if (processed % 10 == 0) {
                        settings.setScanCheckpointId(item.id)
                    }

                    if (processed % 5 == 0) {
                        yield()
                        val elapsedSecs = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.1f)
                        val rate = (processed - startIdx) / elapsedSecs
                        _state.update {
                            it.copy(
                                categorizer = it.categorizer.copy(
                                    processedCount = processed,
                                    progress = (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                                    itemsPerSec = rate,
                                    statusText = "Categorizing: $processed / $total",
                                    errorCount = errors.size,
                                    errors = errors.toList()
                                )
                            )
                        }
                    }
                }

                // Finished all items cleanly; clear checkpoint
                settings.setScanCheckpointId(0L)

                _state.update {
                    it.copy(
                        categorizer = it.categorizer.copy(
                            isRunning = false,
                            isPaused = false,
                            progress = 1.0f,
                            statusText = if (errors.isEmpty()) "Completed" else "Done with ${errors.size} errors",
                            errorCount = errors.size,
                            errors = errors.toList()
                        )
                    )
                }
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(
                        categorizer = it.categorizer.copy(
                            isRunning = false,
                            isPaused = false,
                            statusText = "Cancelled"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Categorizer pipeline error: ${e.message}")
                _state.update {
                    it.copy(
                        categorizer = it.categorizer.copy(
                            isRunning = false,
                            statusText = "Error: ${e.message?.take(30)}"
                        )
                    )
                }
            } finally {
                updateAnyRunning()
            }
        }
    }

    // ─── Pipeline 3: Face Clustering & Embedding Pipeline ─────────────────

    fun startFacesPipeline() {
        if (jobFaces?.isActive == true) return
        pauseFaces = false
        jobFaces = orchestratorScope.launch(Dispatchers.Default.limitedParallelism(1)) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            _state.update {
                it.copy(
                    faces = it.faces.copy(
                        isRunning = true,
                        isPaused = false,
                        progress = 0.1f,
                        statusText = "Clustering faces..."
                    )
                )
            }
            updateAnyRunning()

            try {
                val threshold = settings.settingsFlow.first().faceClusterThreshold
                engine.scanFaces(threshold = threshold, forceRescan = false)

                _state.update {
                    it.copy(
                        faces = it.faces.copy(
                            isRunning = false,
                            isPaused = false,
                            progress = 1.0f,
                            statusText = "Completed"
                        )
                    )
                }
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(
                        faces = it.faces.copy(
                            isRunning = false,
                            isPaused = false,
                            statusText = "Cancelled"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Face pipeline error: ${e.message}")
                _state.update {
                    it.copy(
                        faces = it.faces.copy(
                            isRunning = false,
                            statusText = "Error: ${e.message?.take(30)}"
                        )
                    )
                }
            } finally {
                updateAnyRunning()
            }
        }
    }

    // ─── Multi-Pipeline Controls ──────────────────────────────────────────

    fun startAllPipelines() {
        startDuplicatesPipeline()
        startCategorizerPipeline()
        startFacesPipeline()
    }

    fun togglePauseDuplicates() {
        pauseDuplicates = !pauseDuplicates
        _state.update {
            it.copy(
                duplicates = it.duplicates.copy(
                    isPaused = pauseDuplicates,
                    statusText = if (pauseDuplicates) "Paused" else "Resuming..."
                )
            )
        }
    }

    fun togglePauseCategorizer() {
        pauseCategorizer = !pauseCategorizer
        _state.update {
            it.copy(
                categorizer = it.categorizer.copy(
                    isPaused = pauseCategorizer,
                    statusText = if (pauseCategorizer) "Paused" else "Resuming..."
                )
            )
        }
    }

    fun togglePauseFaces() {
        pauseFaces = !pauseFaces
        _state.update {
            it.copy(
                faces = it.faces.copy(
                    isPaused = pauseFaces,
                    statusText = if (pauseFaces) "Paused" else "Resuming..."
                )
            )
        }
    }

    fun cancelDuplicates() {
        jobDuplicates?.cancel()
        jobDuplicates = null
        pauseDuplicates = false
    }

    fun cancelCategorizer() {
        jobCategorizer?.cancel()
        jobCategorizer = null
        pauseCategorizer = false
    }

    fun cancelFaces() {
        jobFaces?.cancel()
        jobFaces = null
        pauseFaces = false
    }

    fun cancelAll() {
        cancelDuplicates()
        cancelCategorizer()
        cancelFaces()
    }

    fun clearErrors() {
        _state.update {
            it.copy(
                duplicates = it.duplicates.copy(errorCount = 0, errors = emptyList()),
                categorizer = it.categorizer.copy(errorCount = 0, errors = emptyList()),
                faces = it.faces.copy(errorCount = 0, errors = emptyList())
            )
        }
    }
}
