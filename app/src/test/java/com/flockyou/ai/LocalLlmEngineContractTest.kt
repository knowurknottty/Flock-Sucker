package com.flockyou.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the LocalLlmEngine lifecycle: state transitions,
 * streaming, cancellation, error recovery, and unload.
 */
class LocalLlmEngineContractTest {

    @Test
    fun `lifecycle legal path transitions succeed`() {
        val m = LocalModelStateMachine()
        assertTrue(m.transition(LocalModelState.INSTALLED))
        assertTrue(m.transition(LocalModelState.LOADING))
        assertTrue(m.markReadyAfterSmokeTest())
        assertEquals(LocalModelState.READY, m.state.value)
    }

    @Test
    fun `ready cannot be reached without loading`() {
        val m = LocalModelStateMachine()
        assertFalse(m.transition(LocalModelState.READY)) // skip INSTALL/LOAD
        assertFalse(m.markReadyAfterSmokeTest())
        assertEquals(LocalModelState.NOT_INSTALLED, m.state.value)
    }

    @Test
    fun `download cannot jump to ready`() {
        val m = LocalModelStateMachine()
        assertTrue(m.transition(LocalModelState.DOWNLOADING))
        assertFalse(m.transition(LocalModelState.READY))
        assertEquals(LocalModelState.DOWNLOADING, m.state.value)
    }

    @Test
    fun `error state allows recovery to installed and reload`() {
        val m = LocalModelStateMachine()
        m.transition(LocalModelState.INSTALLED)
        m.transition(LocalModelState.LOADING)
        assertTrue(m.transition(LocalModelState.ERROR, "native crash"))
        assertTrue(m.transition(LocalModelState.INSTALLED))
        assertTrue(m.transition(LocalModelState.LOADING))
        assertTrue(m.markReadyAfterSmokeTest())
    }

    @Test
    fun `fake engine load reaches ready with smoke test`() = runTest {
        val engine = FakeLocalLlmEngine()
        engine.load("/models/gemma-flock-q8-0.gguf", LocalModelConfig())
        assertEquals(LocalModelState.READY, engine.health().state)
        assertEquals("gemma-flock-q8-0.gguf", engine.health().loadedModelId)
        assertTrue(engine.health().lastSmokeTestAt != null)
    }

    @Test
    fun `generation streams tokens then completes`() = runTest {
        val engine = FakeLocalLlmEngine()
        engine.load("/models/m.gguf", LocalModelConfig())
        val events = engine.generate(GenerationRequest("r1", "watch the watchers")).toList()
        val tokens = events.filterIsInstance<GenerationEvent.Token>()
        assertTrue(events.last() is GenerationEvent.Completed)
        assertEquals(3, tokens.size)
        assertEquals(3, (events.last() as GenerationEvent.Completed).tokenCount)
    }

    @Test
    fun `generation fails cleanly when not ready`() = runTest {
        val engine = FakeLocalLlmEngine()
        val events = engine.generate(GenerationRequest("r2", "prompt")).toList()
        assertTrue(events.first() is GenerationEvent.Failed)
    }

    @Test
    fun `cancel stops a generation`() = runTest {
        val engine = FakeLocalLlmEngine()
        engine.load("/models/m.gguf", LocalModelConfig())
        engine.cancel("r3")
        val events = engine.generate(GenerationRequest("r3", "a b c d e")).toList()
        // All tokens pre-cancelled are dropped by takeWhile on the Cancelled event
        assertTrue(events.none { it is GenerationEvent.Completed })
        assertTrue(events.any { it is GenerationEvent.Cancelled } || events.isEmpty())
    }

    @Test
    fun `unload returns to installed with no loaded model`() = runTest {
        val engine = FakeLocalLlmEngine()
        engine.load("/models/m.gguf", LocalModelConfig())
        engine.unload()
        assertEquals(LocalModelState.INSTALLED, engine.health().state)
        assertEquals(null, engine.health().loadedModelId)
    }
}
