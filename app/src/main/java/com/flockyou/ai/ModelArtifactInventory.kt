package com.flockyou.ai

import com.flockyou.data.AiModel
import com.flockyou.data.ModelFormat
import java.io.File

internal object ModelArtifactInventory {
    private const val MIN_GENERIC_ARTIFACT_BYTES = 1_000L
    private const val MIN_TASK_ARTIFACT_BYTES = 10_000_000L

    fun isInstalled(model: AiModel, modelDir: File): Boolean = when (model.modelFormat) {
        ModelFormat.GGUF -> {
            val file = File(modelDir, "${model.id}.gguf")
            file.isFile && file.length() > MIN_GENERIC_ARTIFACT_BYTES
        }
        ModelFormat.TASK -> {
            val taskFile = File(modelDir, "${model.id}.task")
            val binFile = File(modelDir, "${model.id}.bin")
            (taskFile.isFile && taskFile.length() > MIN_TASK_ARTIFACT_BYTES) ||
                (binFile.isFile && binFile.length() > MIN_TASK_ARTIFACT_BYTES)
        }
        else -> false
    }
}
