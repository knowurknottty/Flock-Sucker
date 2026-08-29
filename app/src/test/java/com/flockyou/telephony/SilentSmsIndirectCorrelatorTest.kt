package com.flockyou.telephony

import org.junit.Assert.*
import org.junit.Test

class SilentSmsIndirectCorrelatorTest {
    @Test fun requiresMultipleIndependentObservablesAndLabelsBoundary() {
        val c = SilentSmsIndirectCorrelator(windowMs = 15_000L, cooldownMs = 0L)
        val t = 1_000_000L
        assertNull(c.observe(TelephonyObservation(TelephonyObservable.SERVICE_STATE_CHURN, t, "0->1")))
        assertNull(c.observe(TelephonyObservation(TelephonyObservable.CELL_RESELECTION, t + 1000, "a->b")))
        assertNull(c.observe(TelephonyObservation(TelephonyObservable.RAT_CHANGE, t + 2000, "20->13")))
        val evidence = c.observe(TelephonyObservation(TelephonyObservable.SIGNAL_DISCONTINUITY, t + 3000, "-95->-65"))
        assertNotNull(evidence)
        assertEquals(SilentSmsEvidenceClass.INDIRECT, evidence!!.evidenceClass)
        assertEquals("INDIRECT / NOT PROOF OF SILENT SMS", evidence.label)
        assertTrue(evidence.confidence < 0.70f)
        assertTrue(evidence.proofBoundary.contains("No Type-0 payload"))
    }

    @Test fun staleObservationsFallOutOfCorrelationWindow() {
        val c = SilentSmsIndirectCorrelator(windowMs = 5_000L, cooldownMs = 0L)
        val t = 1_000_000L
        c.observe(TelephonyObservation(TelephonyObservable.SERVICE_STATE_CHURN, t, "x"))
        c.observe(TelephonyObservation(TelephonyObservable.CELL_RESELECTION, t + 1000, "x"))
        c.observe(TelephonyObservation(TelephonyObservable.RAT_CHANGE, t + 2000, "x"))
        assertNull(c.observe(TelephonyObservation(TelephonyObservable.SIGNAL_DISCONTINUITY, t + 8000, "x")))
    }
}
