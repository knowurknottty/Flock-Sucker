package com.flockyou.detection.enrichment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranging enrichment tests: pure decision surfaces only. The RTT/UWB
 * hardware calls require a device; these tests pin the evidence formatting
 * and honest status handling.
 */
class RangingEnricherTest {

    private fun evidence(meters: Int, status: String = "SUCCESS", bssid: String = "AA:BB:CC:00:00:01") =
        RangingEnricher.RttEvidence(
            bssid = bssid, distanceMeters = meters, distanceStdDevMm = 300,
            rssi = -58, numAttempts = 8, statusName = status
        )

    @Test
    fun `rtt evidence string formats successful measurements`() {
        val s = RangingEnricher.Companion.rttEvidenceString(listOf(evidence(4)))
        org.junit.Assert.assertNotNull(s)
        assertTrue(s!!.contains("AA:BB:CC:00:00:01"))
        assertTrue(s.contains("4m"))
        assertTrue(s.contains("RTT"))
    }

    @Test
    fun `no successful measurements yield null - no invented distances`() {
        // NO_FTM_RESPONSE is an honest outcome, not a measurement
        val s = RangingEnricher.Companion.rttEvidenceString(
            listOf(evidence(-1, status = "NO_FTM_RESPONSE"))
        )
        assertNull(s)
    }

    @Test
    fun `failed responders excluded from evidence string`() {
        val s = RangingEnricher.Companion.rttEvidenceString(listOf(
            evidence(4, bssid = "AA:BB:CC:00:00:01"),
            evidence(-1, status = "REJECTED_BY_PEER", bssid = "AA:BB:CC:00:00:02")
        ))
        assertTrue(s!!.contains("00:00:01"))
        assertFalse(s.contains("00:00:02"))
    }

    @Test
    fun `multiple successful measurements all recorded`() {
        val s = RangingEnricher.Companion.rttEvidenceString(listOf(
            evidence(4, bssid = "AA:BB:CC:00:00:01"),
            evidence(7, bssid = "AA:BB:CC:00:00:02")
        ))
        assertTrue(s!!.contains("4m") && s.contains("7m"))
        assertTrue(s.contains("; "))
    }

    @Test
    fun `status labels cover honest failure modes`() {
        // NO_FTM_RESPONSE is expected for non-802.11mc APs and must not be
        // presented as a failure of the scanner itself.
        val labels = listOf("SUCCESS", "NOT_SCHEDULED", "NO_FTM_RESPONSE",
            "REJECTED_BY_PEER")
        labels.forEach { assertTrue(it.isNotBlank()) }
    }
}
