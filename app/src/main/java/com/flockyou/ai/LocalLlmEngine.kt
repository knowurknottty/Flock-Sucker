package com.flockyou.ai

import kotlinx.coroutines.flow.Flow

/**
 * Local LLM engine contract. Implementations own the full model lifecycle:
 * load -> generate (streaming) -> cancel -> unload. A model is READY only
 * after a real inference smoke test succeeds — download or file existence is
 * never sufficient (no fake support).
 */
interface LocalLlmEngine {

    /** Load a verified, on-disk model. Suspends until READY or throws. */
    suspend fun load(modelPath: String, config: LocalModelConfig)

    /** Generate tokens for a prompt as a cold streaming Flow. */
    fun generate(request: GenerationRequest): Flow<GenerationEvent>

    /** Cancel an in-flight generation by request id. */
    fun cancel(requestId: String)

    /** Unload the model and free native resources. */
    suspend fun unload()

    /** Current engine health (model state + last error). */
    fun health(): LocalEngineHealth
}

/** Immutable model config handed to [LocalLlmEngine.load]. */
data class LocalModelConfig(
    val contextTokens: Int = 2048,
    val threads: Int = 4,
    val temperature: Float = 0.7f
)

/** One generation request. */
data class GenerationRequest(
    val requestId: String,
    val prompt: String,
    val maxTokens: Int = 256,
    val stopSequences: List<String> = emptyList()
)

/** Streamed generation events. */
sealed class GenerationEvent {
    data class Token(val requestId: String, val text: String) : GenerationEvent()
    data class Completed(val requestId: String, val tokenCount: Int) : GenerationEvent()
    data class Failed(val requestId: String, val error: String) : GenerationEvent()
    data class Cancelled(val requestId: String) : GenerationEvent()
}

/** Model lifecycle states. READY requires a real inference smoke test. */
enum class LocalModelState {
    NOT_INSTALLED, DOWNLOADING, VERIFYING, INSTALLED, LOADING, READY, ERROR
}

/** Engine health snapshot for the local GGUF lifecycle (distinct from the
 *  legacy per-cloud-engine [com.flockyou.ai.EngineHealth] counters). */
data class LocalEngineHealth(
    val state: LocalModelState,
    val loadedModelId: String? = null,
    val lastError: String? = null,
    val lastSmokeTestAt: Long? = null
)
