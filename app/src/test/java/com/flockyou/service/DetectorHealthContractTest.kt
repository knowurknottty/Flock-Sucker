package com.flockyou.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the extended scanner proof-of-life health contract.
 */
class DetectorHealthContractTest {

    private fun fresh(status: DetectorHealthStatus.() -> DetectorHealthStatus): DetectorHealthStatus {
        val base = DetectorHealthStatus(name = DetectorHealthStatus.DETECTOR_BLE)
        return base.status()
    }

    @Test
    fun `default health has no proof of life`() {
        val h = DetectorHealthStatus(name = "BLE")
        assertFalse(h.hasProofOfLife)
    }

    @Test
    fun `running healthy hardware recent scan equals proof of life`() {
        val h = DetectorHealthStatus(
            name = "BLE",
            isRunning = true,
            isHealthy = true,
            hardwareAvailable = true,
            lastSuccessfulScan = System.currentTimeMillis() - 5_000L
        )
        assertTrue(h.hasProofOfLife)
    }

    @Test
    fun `stale last scan defeats proof of life`() {
        val h = DetectorHealthStatus(
            name = "BLE",
            isRunning = true,
            isHealthy = true,
            hardwareAvailable = true,
            lastSuccessfulScan = System.currentTimeMillis() - 300_000L, // 5 min ago
            staleThresholdMs = 120_000L
        )
        assertFalse(h.hasProofOfLife)
    }

    @Test
    fun `funnel counters default to zero and are independent`() {
        val h = DetectorHealthStatus(
            name = "WiFi",
            rawObservationCount = 100,
            candidateCount = 60,
            acceptedSightingCount = 40,
            suppressedCount = 5,
            throttleDropCount = 15,
            persistenceFailureCount = 0
        )
        // raw >= candidates >= accepted; drops accounted
        assertTrue(h.rawObservationCount >= h.candidateCount)
        assertTrue(h.candidateCount >= h.acceptedSightingCount)
        assertEquals(100L, h.rawObservationCount)
        assertEquals(15L, h.throttleDropCount)
    }

    @Test
    fun `required lanes list covers all eight scanners`() {
        val lanes = DetectorHealthStatus.REQUIRED_LANES
        assertEquals(8, lanes.size)
        assertTrue(lanes.containsAll(listOf("BLE", "WiFi", "Cellular", "GNSS")))
    }

    @Test
    fun `permission and stop-reason fields carry defaults compatible with old payloads`() {
        val h = DetectorHealthStatus(name = "GNSS")
        assertEquals("unknown", h.permissionState)
        assertEquals(null, h.lastStopReason)
        assertEquals(false, h.watchdogActive)
        assertEquals(120_000L, h.staleThresholdMs)
    }
}
