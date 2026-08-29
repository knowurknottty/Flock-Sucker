package com.flockyou.adversarial

import org.junit.Assert.*
import org.junit.Test

class BleCoTravelerAnalyzerTest {
    @Test fun fingerprintIsMacIndependentAndOrderStable() {
        val analyzer = BleCoTravelerAnalyzer()
        val a = BleFingerprintInput(mapOf(76 to 8, 117 to 4), listOf("B", "a"), mapOf("Y" to 3, "x" to 7), -12, "8:2:6")
        val b = BleFingerprintInput(linkedMapOf(117 to 4, 76 to 8), listOf("a", "B"), linkedMapOf("x" to 7, "Y" to 3), -12, "8:2:6")
        assertEquals(analyzer.fingerprint(a), analyzer.fingerprint(b))
    }

    @Test fun requiresTemporalComotionAcrossThreeRotatingMacs() {
        val analyzer = BleCoTravelerAnalyzer(alertCooldownMs = 0)
        val fp = "fixture"
        val t0 = 1_000_000L
        assertNull(analyzer.observe(fp, BleTailObservation("A", 0.00, 0.0, t0, -60)))
        assertNull(analyzer.observe(fp, BleTailObservation("B", 0.04, 0.0, t0 + 10 * 60_000L, -61)))
        val alert = analyzer.observe(fp, BleTailObservation("C", 0.08, 0.0, t0 + 20 * 60_000L, -62))
        assertNotNull(alert)
        assertEquals(3, alert!!.distinctMacs)
        assertTrue(alert.separatedLocations >= 3)
        assertTrue(alert.maxSeparationMeters > 6_000.0)
        assertTrue(alert.journeyDurationMs >= 8 * 60_000L)
        assertTrue(alert.continuityRatio >= 0.70f)
        assertTrue(alert.proofBoundary.contains("not proof", ignoreCase = true))
    }

    @Test fun rejectsSameModelDevicesEncounteredHoursApart() {
        val analyzer = BleCoTravelerAnalyzer(alertCooldownMs = 0)
        val fp = "same-model"
        val t0 = 2_000_000L
        assertNull(analyzer.observe(fp, BleTailObservation("A", 0.00, 0.0, t0, -55)))
        assertNull(analyzer.observe(fp, BleTailObservation("B", 0.04, 0.0, t0 + 4 * 60 * 60_000L, -70)))
        assertNull(analyzer.observe(fp, BleTailObservation("C", 0.08, 0.0, t0 + 8 * 60 * 60_000L, -48)))
    }

    @Test fun rejectsImplausibleTeleportEvenInsideTimeWindow() {
        val analyzer = BleCoTravelerAnalyzer(alertCooldownMs = 0)
        val fp = "teleport"
        val t0 = 3_000_000L
        assertNull(analyzer.observe(fp, BleTailObservation("A", 0.00, 0.0, t0, -60)))
        assertNull(analyzer.observe(fp, BleTailObservation("B", 0.10, 0.0, t0 + 30_000L, -60)))
        assertNull(analyzer.observe(fp, BleTailObservation("C", 0.20, 0.0, t0 + 60_000L, -60)))
    }
}
