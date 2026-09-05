package com.flockyou.service

import com.flockyou.data.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class UltrasonicHealthPolicyTest {
    private fun status(
        isScanning: Boolean = true,
        frameReadCount: Long = 0,
        analysisCycleCount: Long = 0,
        lastFrameTime: Long? = null,
        lastAnalysisTime: Long? = null,
        gateReason: String? = null,
        lastError: String? = null,
        staleAfterMs: Long = 1_000L
    ) = UltrasonicDetector.UltrasonicStatus(
        isScanning = isScanning,
        lastScanTime = 0L,
        noiseFloorDb = -60.0,
        ultrasonicActivityDetected = false,
        activeBeaconCount = 0,
        peakFrequency = null,
        peakAmplitudeDb = null,
        threatLevel = ThreatLevel.INFO,
        frameReadCount = frameReadCount,
        analysisCycleCount = analysisCycleCount,
        lastFrameTime = lastFrameTime,
        lastAnalysisTime = lastAnalysisTime,
        gateReason = gateReason,
        lastError = lastError,
        proofStaleAfterMs = staleAfterMs
    )

    @Test
    fun `gated status is explicit`() {
        assertEquals(
            UltrasonicLifecycleState.GATED,
            UltrasonicHealthPolicy.state(status(isScanning = false, gateReason = "consent_required"), 1_000L)
        )
    }

    @Test
    fun `monitor is starting until an audio frame is read`() {
        assertEquals(UltrasonicLifecycleState.STARTING, UltrasonicHealthPolicy.state(status(), 1_000L))
    }

    @Test
    fun `frame without analysis is proving not proven`() {
        val status = status(frameReadCount = 1, lastFrameTime = 990L)
        assertEquals(UltrasonicLifecycleState.PROVING, UltrasonicHealthPolicy.state(status, 1_000L))
    }

    @Test
    fun `recent frame and analysis heartbeat prove monitoring`() {
        val status = status(
            frameReadCount = 3,
            analysisCycleCount = 3,
            lastFrameTime = 995L,
            lastAnalysisTime = 995L
        )
        assertEquals(UltrasonicLifecycleState.PROVEN, UltrasonicHealthPolicy.state(status, 1_000L))
    }

    @Test
    fun `stale analysis heartbeat is not proven`() {
        val status = status(
            frameReadCount = 3,
            analysisCycleCount = 3,
            lastFrameTime = 100L,
            lastAnalysisTime = 100L
        )
        assertEquals(UltrasonicLifecycleState.STALE, UltrasonicHealthPolicy.state(status, 2_000L))
    }

    @Test
    fun `error dominates monitoring state`() {
        val status = status(
            frameReadCount = 3,
            analysisCycleCount = 3,
            lastFrameTime = 995L,
            lastAnalysisTime = 995L,
            lastError = "AudioRecord failed"
        )
        assertEquals(UltrasonicLifecycleState.FAILED, UltrasonicHealthPolicy.state(status, 1_000L))
    }
}
