package com.flockyou.ui.screens

import com.flockyou.data.AiModel
import com.flockyou.data.AiModelStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTestAnalysisAvailabilityTest {
    @Test
    fun `installed cold GGUF can run test analysis`() {
        assertTrue(
            canRunTestAnalysis(
                modelStatus = AiModelStatus.NotDownloaded,
                selectedModelId = AiModel.FLOCK_GEMMA_Q8_0.id,
                downloadedModels = setOf(AiModel.FLOCK_GEMMA_Q8_0.id)
            )
        )
    }

    @Test
    fun `absent cold GGUF cannot run test analysis`() {
        assertFalse(
            canRunTestAnalysis(
                modelStatus = AiModelStatus.NotDownloaded,
                selectedModelId = AiModel.FLOCK_GEMMA_Q8_0.id,
                downloadedModels = emptySet()
            )
        )
    }

    @Test
    fun `runtime ready model can always run test analysis`() {
        assertTrue(
            canRunTestAnalysis(
                modelStatus = AiModelStatus.Ready,
                selectedModelId = AiModel.FLOCK_GEMMA_Q8_0.id,
                downloadedModels = emptySet()
            )
        )
    }
}
