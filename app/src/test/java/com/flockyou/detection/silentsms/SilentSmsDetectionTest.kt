package com.flockyou.detection.silentsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Silent-SMS hybrid detection tests: indirect anomaly correlation,
 * exact-vs-indirect labeling, confidence bounds, and the proof boundary.
 */
class SilentSmsDetectionTest {

    private fun correlator() = IndirectSilentSmsCorrelator(windowMs = 120_000L)

    private fun obs(kind: TelephonyObservation.Kind, t: Long, detail: String = "", mag: Float? = null) =
        TelephonyObservation(timestampMs = t, kind = kind, detail = detail, magnitude = mag)

    // ==================== Indirect correlation ====================

    @Test
    fun `no observations yield no assessment`() {
        assertNull(correlator().assess())
    }

    @Test
    fun `single weak observation stays below reporting threshold`() {
        val c = correlator()
        c.observe(obs(TelephonyObservation.Kind.SIGNAL_DISCONTINUITY, 1000L))
        assertNull(c.assess())
    }

    @Test
    fun `multi-class correlation inside window produces indirect assessment`() {
        val c = correlator()
        val t = 1000L
        c.observe(obs(TelephonyObservation.Kind.SERVICE_STATE_CHURN, t, "in_service->emergency"))
        c.observe(obs(TelephonyObservation.Kind.RAT_DOWNGRADE, t + 5_000, "LTE->GSM"))
        c.observe(obs(TelephonyObservation.Kind.NEW_CELL_DISCOVERY, t + 10_000, "cell 999-33-12345"))
        c.observe(obs(TelephonyObservation.Kind.SIGNAL_DISCONTINUITY, t + 15_000, "18dB drop", mag = 18f))
        val a = c.assess()
        assertNotNull(a)
        assertEquals(SourceClass.INDIRECT, a!!.sourceClass)
        assertTrue("confidence must be capped below 0.9", a.confidence <= 0.85f)
        assertTrue(a.confidence >= 0.30f)
        assertEquals(4, a.observations.size)
        assertTrue(a.anomalySummary!!.contains("distinct classes"))
        assertTrue(a.anomalySummary!!.contains("Benign alternatives"))
    }

    @Test
    fun `repeated same-kind observations taper - congestion is not silent sms`() {
        val c = correlator()
        // 6 identical service-state churns = weak; taper prevents inflation
        for (i in 0 until 6) {
            c.observe(obs(TelephonyObservation.Kind.SERVICE_STATE_CHURN, 1000L + i * 1_000L))
        }
        val a = c.assess()
        // 0.25 weight with taper: 0.25 * (1 + .5 + .25 + .125 + .0625 + .03125) ≈ 0.49
        // but only ONE distinct kind: no cross-class bonus
        if (a != null) {
            assertTrue(a.confidence <= 0.55f)
            assertEquals(1, a.observations.groupBy { it.kind }.size)
        }
    }

    @Test
    fun `observations outside window are pruned`() {
        val c = correlator()
        c.observe(obs(TelephonyObservation.Kind.SERVICE_STATE_CHURN, 1L))
        c.observe(obs(TelephonyObservation.Kind.RAT_DOWNGRADE, 2L))
        // Assess far in the future: window expired
        assertNull(c.assess(nowMs = 500_000L))
    }

    @Test
    fun `confidence never reaches certainty`() {
        val c = correlator()
        val kinds = TelephonyObservation.Kind.entries
        kinds.forEachIndexed { i, kind ->
            c.observe(obs(kind, 1000L + i * 100L))
        }
        val a = c.assess()
        assertNotNull(a)
        assertTrue("indirect can never claim certainty", a!!.confidence < 0.90f)
    }

    @Test
    fun `proof boundary states the indirect limitation`() {
        val c = correlator()
        c.observe(obs(TelephonyObservation.Kind.SERVICE_STATE_CHURN, 1000L))
        c.observe(obs(TelephonyObservation.Kind.RAT_DOWNGRADE, 2000L))
        c.observe(obs(TelephonyObservation.Kind.NEW_CELL_DISCOVERY, 3000L))
        val a = c.assess()
        assertNotNull(a)
        assertTrue(a!!.proofBoundary.contains("no direct proof"))
        assertTrue(a.proofBoundary.contains("benign causes"))
    }

    // ==================== Exact path ====================

    @Test
    fun `exact sensor unavailable yields no exact assessment`() {
        val sensor = object : ExactSilentSmsSensor {
            override fun isAvailable() = false
            override fun pollLatest() = null
        }
        val detector = HybridSilentSmsDetector(sensor, correlator())
        val results = detector.assess()
        assertTrue(results.none { it.sourceClass == SourceClass.EXACT })
    }

    @Test
    fun `exact event is labeled EXACT with sensor path`() {
        val event = ExactSilentSmsEvent(
            timestampMs = 1000L,
            sensorPath = "shannon-diag/NasMessageParser Type-0 SMS-PP",
            protocolDetail = "TP-PID=0x40 TP-DCS=0xC8"
        )
        val sensor = object : ExactSilentSmsSensor {
            override fun isAvailable() = true
            override fun pollLatest() = event
        }
        val detector = HybridSilentSmsDetector(sensor, correlator())
        val results = detector.assess()
        val exact = results.first { it.sourceClass == SourceClass.EXACT }
        assertEquals("shannon-diag/NasMessageParser Type-0 SMS-PP", exact.sensorPath)
        assertEquals(event, exact.exactEvent)
        assertTrue(exact.proofBoundary.contains("directly observed"))
        assertTrue(exact.confidence > 0.90f)
    }

    @Test
    fun `exact and indirect records are never merged`() {
        val event = ExactSilentSmsEvent(1000L, "shannon-diag", "Type-0")
        val sensor = object : ExactSilentSmsSensor {
            override fun isAvailable() = true
            override fun pollLatest() = event
        }
        val detector = HybridSilentSmsDetector(sensor, correlator())
        detector.observe(obs(TelephonyObservation.Kind.SERVICE_STATE_CHURN, 1000L))
        detector.observe(obs(TelephonyObservation.Kind.RAT_DOWNGRADE, 1100L))
        detector.observe(obs(TelephonyObservation.Kind.NEW_CELL_DISCOVERY, 1200L))
        val results = detector.assess()
        val exact = results.filter { it.sourceClass == SourceClass.EXACT }
        val indirect = results.filter { it.sourceClass == SourceClass.INDIRECT }
        assertEquals(1, exact.size)
        assertEquals(1, indirect.size)
        // Each keeps its own proof boundary
        assertTrue(exact.first().proofBoundary != indirect.first().proofBoundary)
    }

    // ==================== Truth constraints ====================

    @Test
    fun `stock android availability is honestly unavailable without sensor`() {
        // No sensor supplied: the hybrid must not fabricate an EXACT record.
        val detector = HybridSilentSmsDetector(null, correlator())
        val results = detector.assess()
        assertTrue(results.none { it.sourceClass == SourceClass.EXACT })
    }

    @Test
    fun `shannon sensor unavailable when modem diag not verified`() {
        // Availability is capability-bounded, not type-guaranteed.
        val sensor = ShannonExactSilentSmsSensor(
            anomaliesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
            modemDiagVerified = false
        )
        org.junit.Assert.assertFalse(sensor.isAvailable())
        org.junit.Assert.assertNull(sensor.pollLatest())
    }
}
