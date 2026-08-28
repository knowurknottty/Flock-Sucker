package com.flockyou.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile

/**
 * Deterministic state machine implementing the [LocalLlmEngine] lifecycle
 * transitions without binding to llama.cpp. Used as the contract reference
 * implementation and test fake; the native engine will drive the same states.
 */
class LocalModelStateMachine {

    private val _state = MutableStateFlow(LocalModelState.NOT_INSTALLED)
    val state: StateFlow<LocalModelState> = _state

    private val _health = MutableStateFlow(LocalEngineHealth(LocalModelState.NOT_INSTALLED))
    val health: StateFlow<LocalEngineHealth> = _health

    /** Legal forward transitions. Any transition not listed here is rejected. */
    private val allowedTransitions: Map<LocalModelState, Set<LocalModelState>> = mapOf(
        LocalModelState.NOT_INSTALLED to setOf(LocalModelState.DOWNLOADING, LocalModelState.INSTALLED, LocalModelState.ERROR),
        LocalModelState.DOWNLOADING to setOf(LocalModelState.VERIFYING, LocalModelState.ERROR),
        LocalModelState.VERIFYING to setOf(LocalModelState.INSTALLED, LocalModelState.ERROR),
        LocalModelState.INSTALLED to setOf(LocalModelState.LOADING),
        LocalModelState.LOADING to setOf(LocalModelState.READY, LocalModelState.ERROR, LocalModelState.INSTALLED),
        LocalModelState.READY to setOf(LocalModelState.ERROR, LocalModelState.LOADING),
        LocalModelState.ERROR to setOf(LocalModelState.INSTALLED, LocalModelState.LOADING, LocalModelState.NOT_INSTALLED)
    )

    /** Attempt a transition; returns true when accepted. */
    fun transition(to: LocalModelState, error: String? = null): Boolean {
        val from = _state.value
        if (to !in (allowedTransitions[from] ?: emptySet())) return false
        _state.value = to
        _health.value = LocalEngineHealth(
            state = to,
            loadedModelId = _health.value.loadedModelId,
            lastError = error,
            lastSmokeTestAt = if (to == LocalModelState.READY) System.currentTimeMillis() else _health.value.lastSmokeTestAt
        )
        return true
    }

    fun setLoadedModel(modelId: String?) {
        _health.value = _health.value.copy(loadedModelId = modelId)
    }

    /** Ready requires a completed smoke test — assert by construction. */
    fun markReadyAfterSmokeTest(): Boolean {
        if (_state.value != LocalModelState.LOADING) return false
        return transition(LocalModelState.READY)
    }
}

/**
 * Contract-test fake engine: deterministic token streaming over the same
 * lifecycle rules. Proves cancellation, error recovery, and unload semantics
 * without native code.
 */
class FakeLocalLlmEngine : LocalLlmEngine {

    val machine = LocalModelStateMachine()
    private val cancelled = mutableSetOf<String>()

    override suspend fun load(modelPath: String, config: LocalModelConfig) {
        check(machine.transition(LocalModelState.INSTALLED)) { "cannot load from ${machine.state.value}" }
        machine.setLoadedModel(modelPath.substringAfterLast('/'))
        machine.transition(LocalModelState.LOADING)
        // Real engines run an inference smoke test here; the fake succeeds.
        check(machine.markReadyAfterSmokeTest()) { "smoke test failed" }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        if (machine.state.value != LocalModelState.READY) {
            emit(GenerationEvent.Failed(request.requestId, "engine not READY: ${machine.state.value}"))
            return@flow
        }
        val words = request.prompt.split(" ").filter { it.isNotBlank() }
        var count = 0
        var cancelledFlag = false
        for (w in words) {
            if (request.requestId in cancelled) { cancelledFlag = true; break }
            emit(GenerationEvent.Token(request.requestId, w))
            count++
        }
        when {
            cancelledFlag -> emit(GenerationEvent.Cancelled(request.requestId))
            else -> emit(GenerationEvent.Completed(request.requestId, count))
        }
    }.takeWhile { it !is GenerationEvent.Cancelled }

    override fun cancel(requestId: String) {
        cancelled.add(requestId)
    }

    override suspend fun unload() {
        machine.setLoadedModel(null)
        // Unload from READY/ERROR returns through LOADING (unload→install path)
        if (machine.state.value == LocalModelState.READY || machine.state.value == LocalModelState.ERROR) {
            machine.transition(LocalModelState.LOADING)
        }
        machine.transition(LocalModelState.INSTALLED)
    }

    override fun health(): LocalEngineHealth = machine.health.value
}
