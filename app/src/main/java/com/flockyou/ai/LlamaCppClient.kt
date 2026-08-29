package com.flockyou.ai

import android.content.Context
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.flockyou.data.AiAnalysisResult
import com.flockyou.data.AiModel
import com.flockyou.data.model.Detection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaCppClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LlamaCppClient"
        private const val INIT_TIMEOUT_MS = 30_000L
        private const val GENERATION_TIMEOUT_MS = 90_000L
        private const val DEFAULT_MAX_TOKENS = 256
    }
    private val mutex = Mutex()
    private val engine: InferenceEngine by lazy { AiChat.getInferenceEngine(context) }

    @Volatile
    private var ready = false
    private var loadedModelPath: String? = null
    private var activeProfile: LlamaRuntimeProfile? = null
    private var lastError: String? = null

    private val systemPrompt = """
        You are Flock-Sucker's on-device evidence analyst.
        Separate raw observation from inference, state uncertainty, and do not invent capabilities or facts.
        Prefer concise, technically precise analysis grounded only in the supplied evidence.
    """.trimIndent()

    fun isReady(): Boolean = ready
    fun getLastError(): String? = lastError
    fun currentProfile(): LlamaRuntimeProfile? = activeProfile

    suspend fun initialize(
        modelFile: File,
        profile: LlamaRuntimeProfile = LlamaRuntimeProfiles.genericArm64()
    ): Boolean = mutex.withLock {
        lastError = null
        if (!modelFile.exists() || !modelFile.isFile || modelFile.length() < 10_000_000L) {
            lastError = "GGUF model file missing or invalid: ${modelFile.absolutePath}"
            ready = false
            return@withLock false
        }

        if (ready && loadedModelPath == modelFile.absolutePath && activeProfile == profile) {
            return@withLock true
        }

        return@withLock try {
            waitUntilInitialized(throwOnError = false)
            if (engine.state.value !is InferenceEngine.State.Initialized) {
                engine.cleanUp()
                waitUntilInitialized()
            }
            engine.configure(
                contextSize = profile.contextSize,
                batchSize = profile.batchSize,
                microBatchSize = profile.microBatchSize,
                decodeThreads = profile.decodeThreads,
                batchThreads = profile.batchThreads
            )
            engine.loadModel(modelFile.absolutePath)
            engine.setSystemPrompt(systemPrompt)
            loadedModelPath = modelFile.absolutePath
            activeProfile = profile
            ready = true
            Log.i(TAG, "llama.cpp model ready: ${modelFile.name}; profile=${profile.id}")
            true
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            ready = false
            loadedModelPath = null
            Log.e(TAG, "llama.cpp initialization failed", t)
            false
        }
    }

    private suspend fun waitUntilInitialized(throwOnError: Boolean = true) {
        withTimeout(INIT_TIMEOUT_MS) {
            when (engine.state.value) {
                is InferenceEngine.State.Uninitialized,
                is InferenceEngine.State.Initializing -> engine.state.first {
                    it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
                }
                else -> Unit
            }
        }
        val error = engine.state.value as? InferenceEngine.State.Error
        if (throwOnError && error != null) throw error.exception
    }
    suspend fun generateResponse(
        prompt: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): String? = mutex.withLock {
        if (!ready) return@withLock null
        return@withLock try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                val output = StringBuilder()
                engine.sendUserPrompt(prompt, maxTokens).collect { chunk ->
                    output.append(chunk)
                }
                output.toString().trim().takeIf { it.isNotEmpty() }
            }
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            ready = false
            Log.e(TAG, "llama.cpp generation failed", t)
            null
        }
    }

    suspend fun selfTest(): Boolean {
        val response = generateResponse("Reply with exactly the word READY.", maxTokens = 16)
        return !response.isNullOrBlank()
    }
    suspend fun analyzeDetection(
        detection: Detection,
        enrichedData: EnrichedDetectorData? = null,
        privilegeMode: com.flockyou.privilege.PrivilegeMode? = null
    ): AiAnalysisResult {
        val start = System.currentTimeMillis()
        val prompt = PromptTemplates.buildStructuredOutputPrompt(detection, enrichedData, privilegeMode)
        val response = generateResponse(prompt)
        return if (response != null) {
            AiAnalysisResult(
                success = true,
                analysis = response,
                confidence = 0.85f,
                processingTimeMs = System.currentTimeMillis() - start,
                modelUsed = AiModel.FLOCK_GEMMA_Q8_0.id,
                wasOnDevice = true
            )
        } else {
            AiAnalysisResult(
                success = false,
                error = lastError ?: "llama.cpp returned empty output",
                processingTimeMs = System.currentTimeMillis() - start,
                modelUsed = AiModel.FLOCK_GEMMA_Q8_0.id,
                wasOnDevice = true
            )
        }
    }
    suspend fun benchmark(
        promptProcessingTokens: Int = 128,
        generatedTokens: Int = 32,
        parallelSequences: Int = 1,
        repetitions: Int = 3
    ): String? = mutex.withLock {
        if (!ready) return@withLock null
        return@withLock try {
            engine.bench(promptProcessingTokens, generatedTokens, parallelSequences, repetitions)
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "llama.cpp benchmark failed", t)
            null
        }
    }

    suspend fun cleanup() = mutex.withLock {
        if (engine.state.value is InferenceEngine.State.ModelReady ||
            engine.state.value is InferenceEngine.State.Error) {
            engine.cleanUp()
        }
        ready = false
        loadedModelPath = null
        activeProfile = null
    }


    fun cleanupSync() {
        try {
            if (engine.state.value is InferenceEngine.State.ModelReady ||
                engine.state.value is InferenceEngine.State.Error) {
                engine.cleanUp()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "llama.cpp synchronous cleanup failed", t)
        } finally {
            ready = false
            loadedModelPath = null
            activeProfile = null
        }
    }
}
