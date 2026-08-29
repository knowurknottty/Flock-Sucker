package com.flockyou.ai

/** Runtime parameters for the bundled llama.cpp backend. */
data class LlamaRuntimeProfile(
    val id: String,
    val decodeThreads: Int,
    val batchThreads: Int,
    val contextSize: Int,
    val batchSize: Int,
    val microBatchSize: Int
)

object LlamaRuntimeProfiles {
    /**
     * Conservative portable ARM64 defaults. Device-specialized branches may
     * override these values only after physical-device measurement.
     */
    fun genericArm64(availableProcessors: Int = Runtime.getRuntime().availableProcessors()): LlamaRuntimeProfile {
        val threads = availableProcessors.coerceIn(1, 4)
        return LlamaRuntimeProfile(
            id = "generic-arm64",
            decodeThreads = threads,
            batchThreads = threads,
            contextSize = 2048,
            batchSize = 128,
            microBatchSize = 32
        )
    }
}
