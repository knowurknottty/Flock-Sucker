package com.flockyou.adversarial

import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class OpticalPulseWindowTest {
    @Test fun rhythmicLowLightModulationProducesHeuristicCandidate() {
        val window = OpticalPulseWindow(maxSamples = 64, minSamples = 24)
        var state = OpticalPulseState()
        repeat(64) { i ->
            val t = i / 30.0
            val luma = (60.0 + 22.0 * sin(2.0 * Math.PI * 5.0 * t)).toFloat()
            state = window.add(LumaSample((t * 1_000_000_000L).toLong(), luma))
        }
        assertTrue(state.candidateDetected)
        assertNotNull(state.estimatedHz)
        assertTrue(state.confidence in 0.42f..0.78f)
        assertTrue(state.proofBoundary.contains("NOT WAVELENGTH PROOF"))
    }

    @Test fun flatLowLightSceneDoesNotTrigger() {
        val window = OpticalPulseWindow(maxSamples = 64, minSamples = 24)
        var state = OpticalPulseState()
        repeat(40) { i -> state = window.add(LumaSample(i * 33_333_333L, 60f)) }
        assertFalse(state.candidateDetected)
    }
}
