package com.inversionlabs.flocksucker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPreferredModelContractTest {
    @Test
    fun `fresh AI settings prefer the Inversion Labs fine tuned Gemma`() {
        assertEquals(AiModel.FLOCK_GEMMA_Q8_0.id, AiSettings().selectedModel)
    }

    @Test
    fun `preferred Gemma acquisition instructions expose canonical hosted artifact`() {
        val instructions = AiModel.getDownloadInstructions(AiModel.FLOCK_GEMMA_Q8_0)

        assertTrue(instructions.contains("https://mega.nz/file/WzAiwIba#-lYBgLIkxmAgzmd_CXcKEjMIhuuYlvpfWFUeVXMnxlc"))
        assertTrue(instructions.contains("SHA-256", ignoreCase = true) || instructions.contains("hash", ignoreCase = true))
        assertTrue(instructions.contains("Import Model"))
    }
}
