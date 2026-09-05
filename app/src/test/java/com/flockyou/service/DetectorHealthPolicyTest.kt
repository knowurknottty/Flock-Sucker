package com.flockyou.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorHealthPolicyTest {
    @Test
    fun `expected stopped lane requires attention and suppresses all systems ok`() {
        val now = 1_000_000L
        val statuses = DetectorHealthStatus.REQUIRED_LANES.associateWith { lane ->
            DetectorHealthStatus(
                name = lane,
                expectedToRun = true,
                isRunning = lane != DetectorHealthStatus.DETECTOR_WIFI,
                hardwareAvailable = true,
                lastSuccessfulScan = if (lane != DetectorHealthStatus.DETECTOR_WIFI) now else null,
                lastHeartbeatTime = if (lane != DetectorHealthStatus.DETECTOR_WIFI) now else null
            )
        }

        val summary = DetectorHealthPolicy.summarize(statuses, now)
        assertFalse(summary.allSystemsOk)
        assertEquals(1, summary.attentionCount)
        assertEquals(7, summary.healthyCount)
    }

    @Test
    fun `privacy gated lane is explicit and never counted healthy`() {
        val gated = DetectorHealthStatus(
            name = DetectorHealthStatus.DETECTOR_ULTRASONIC,
            expectedToRun = false,
            gateReason = "consent_required"
        )

        assertEquals(DetectorLifecycleState.BLOCKED, DetectorHealthPolicy.state(gated, 1_000L))
        val summary = DetectorHealthPolicy.summarize(mapOf(gated.name to gated), 1_000L)
        assertEquals(1, summary.blockedCount)
        assertEquals(0, summary.healthyCount)
        assertFalse(summary.allSystemsOk)
    }

    @Test
    fun `started detector is starting until proof of life heartbeat arrives`() {
        val status = DetectorHealthStatus(
            name = DetectorHealthStatus.DETECTOR_BLE,
            expectedToRun = true,
            isRunning = true,
            isHealthy = true,
            hardwareAvailable = true,
            lastStartTime = 900L
        )

        assertEquals(DetectorLifecycleState.STARTING, DetectorHealthPolicy.state(status, 1_000L))
        val proven = status.copy(lastHeartbeatTime = 995L, lastSuccessfulScan = 995L)
        assertEquals(DetectorLifecycleState.RUNNING, DetectorHealthPolicy.state(proven, 1_000L))
        assertTrue(DetectorHealthPolicy.isOperational(proven, 1_000L))
    }

    @Test
    fun `stale heartbeat is attention even while detector flag remains running`() {
        val status = DetectorHealthStatus(
            name = DetectorHealthStatus.DETECTOR_WIFI,
            expectedToRun = true,
            isRunning = true,
            isHealthy = true,
            hardwareAvailable = true,
            lastStartTime = 100L,
            lastSuccessfulScan = 200L,
            lastHeartbeatTime = 200L,
            staleThresholdMs = 500L
        )

        assertEquals(DetectorLifecycleState.STALE, DetectorHealthPolicy.state(status, 1_000L))
        val summary = DetectorHealthPolicy.summarize(mapOf(status.name to status), 1_000L)
        assertEquals(1, summary.attentionCount)
        assertEquals(1, summary.staleCount)
        assertFalse(summary.allSystemsOk)
    }
}
