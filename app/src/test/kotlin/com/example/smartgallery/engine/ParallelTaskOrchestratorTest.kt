package com.example.smartgallery.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParallelTaskOrchestratorTest {

    @Test
    fun testPipelineProgress_initialState() {
        val progress = PipelineProgress(title = "Smart Categorizer")
        assertThat(progress.title).isEqualTo("Smart Categorizer")
        assertThat(progress.progress).isEqualTo(0f)
        assertThat(progress.isRunning).isFalse()
        assertThat(progress.errorCount).isEqualTo(0)
        assertThat(progress.errors).isEmpty()
    }

    @Test
    fun testPipelineProgress_errorTracking() {
        val error1 = PipelineError(itemName = "corrupt_image.jpg", reason = "OutOfMemory during bitmap decoding")
        val error2 = PipelineError(itemName = "invalid_exif.png", reason = "Failed to parse EXIF metadata")

        val progress = PipelineProgress(
            title = "Smart Categorizer",
            processedCount = 50,
            totalCount = 100,
            progress = 0.5f,
            isRunning = true,
            errorCount = 2,
            errors = listOf(error1, error2)
        )

        assertThat(progress.errorCount).isEqualTo(2)
        assertThat(progress.errors).hasSize(2)
        assertThat(progress.errors[0].itemName).isEqualTo("corrupt_image.jpg")
        assertThat(progress.errors[1].reason).contains("EXIF")
    }

    @Test
    fun testOrchestratorState_aggregation() {
        val state = OrchestratorState(
            duplicates = PipelineProgress("Duplicates", isRunning = false),
            categorizer = PipelineProgress("Categorizer", isRunning = true),
            faces = PipelineProgress("Faces", isRunning = false),
            isAnyRunning = true
        )

        assertThat(state.isAnyRunning).isTrue()
        assertThat(state.categorizer.isRunning).isTrue()
        assertThat(state.duplicates.isRunning).isFalse()
    }
}
