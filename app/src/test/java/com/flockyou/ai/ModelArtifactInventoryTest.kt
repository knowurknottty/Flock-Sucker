package com.flockyou.ai

import com.flockyou.data.AiModel
import java.io.File
import java.nio.file.Files
import java.io.RandomAccessFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArtifactInventoryTest {
    @Test
    fun `manual GGUF artifact is inventoried as installed`() {
        val dir = Files.createTempDirectory("flock-gguf-inventory").toFile()
        try {
            File(dir, "${AiModel.FLOCK_GEMMA_Q8_0.id}.gguf")
                .writeBytes(ByteArray(2_048) { 1 })
            assertTrue(ModelArtifactInventory.isInstalled(AiModel.FLOCK_GEMMA_Q8_0, dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing GGUF artifact is not inventoried as installed`() {
        val dir = Files.createTempDirectory("flock-gguf-missing").toFile()
        try {
            assertFalse(ModelArtifactInventory.isInstalled(AiModel.FLOCK_GEMMA_Q8_0, dir))
        } finally {
            dir.deleteRecursively()
        }
    }
    @Test
    fun `large TASK artifact remains inventoried as installed`() {
        val dir = Files.createTempDirectory("flock-task-inventory").toFile()
        try {
            RandomAccessFile(File(dir, "${AiModel.GEMMA3_1B.id}.task"), "rw").use {
                it.setLength(10_000_001L)
            }
            assertTrue(ModelArtifactInventory.isInstalled(AiModel.GEMMA3_1B, dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `tiny TASK artifact is rejected from inventory`() {
        val dir = Files.createTempDirectory("flock-task-tiny").toFile()
        try {
            RandomAccessFile(File(dir, "${AiModel.GEMMA3_1B.id}.task"), "rw").use {
                it.setLength(10_000_000L)
            }
            assertFalse(ModelArtifactInventory.isInstalled(AiModel.GEMMA3_1B, dir))
        } finally {
            dir.deleteRecursively()
        }
    }

}
