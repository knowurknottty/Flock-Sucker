package com.flockyou.ai

import com.flockyou.data.AiModel
import com.flockyou.data.LlmEnginePreference
import com.flockyou.data.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppRuntimeContractTest {
    @Test
    fun `fine tuned Gemma is a runnable GGUF llama model`() {
        val model = AiModel.FLOCK_GEMMA_Q8_0
        assertEquals(ModelFormat.GGUF, model.modelFormat)
        assertEquals(LlmEnginePreference.LLAMA_CPP.id, "llama-cpp")
        assertEquals("gemma-flock-q8-0", model.id)
        assertTrue(FineTunedModelArtifacts.GEMMA_FLOCK_Q8_0.runtimeCompatible)
    }

    @Test
    fun `generic arm64 profile bounds threads conservatively`() {
        val profile = LlamaRuntimeProfiles.genericArm64(availableProcessors = 8)
        assertEquals("generic-arm64", profile.id)
        assertEquals(4, profile.decodeThreads)
        assertEquals(4, profile.batchThreads)
        assertEquals(2048, profile.contextSize)
        assertTrue(profile.microBatchSize <= profile.batchSize)
    }
}
