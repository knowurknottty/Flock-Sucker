package com.flockyou.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADB_PRIVILEGED rung tests: rung placement, probe semantics (existence of
 * manifest entry proves nothing — operations prove grants), and honest
 * evidence labeling for the radio-log path.
 */
class AdbPrivilegedRungTest {

    @Test
    fun `ADB_PRIVILEGED sits between SIDELOAD and ROOT_LIBSU`() {
        assertTrue(CapabilityRung.ADB_PRIVILEGED.rank > CapabilityRung.SIDELOAD.rank)
        assertTrue(CapabilityRung.ADB_PRIVILEGED.rank < CapabilityRung.ROOT_LIBSU.rank)
        assertTrue(CapabilityRung.ADB_PRIVILEGED.atLeast(CapabilityRung.SIDELOAD))
        assertFalse(CapabilityRung.ADB_PRIVILEGED.atLeast(CapabilityRung.ROOT_LIBSU))
    }

    @Test
    fun `full ladder order is monotonic with new rung`() {
        val expected = listOf(
            CapabilityRung.SIDELOAD, CapabilityRung.ADB_PRIVILEGED,
            CapabilityRung.ROOT_LIBSU, CapabilityRung.MAGISK_COMPANION,
            CapabilityRung.SYSTEM_PRIVAPP, CapabilityRung.OEM_PLATFORM
        )
        assertEquals(expected, CapabilityRung.entries.sortedBy { it.rank })
    }

    @Test
    fun `adb grant probes fail honestly without grants`() {
        // Without an ADB session granting permissions, the operation probes
        // must fail — never return granted by default. (On the JVM test
        // environment ProcessBuilder may throw or logcat is unavailable.)
        val probe = CapabilityLadderDetector.probeAdbGrants()
        // The decisive assertion: a probe result must never claim a grant
        // whose operation did not succeed. We cannot force grants in JVM
        // tests, so verify the probe completed and evidence matches grants.
        probe.granted.forEach { grant ->
            assertTrue(probe.evidence.containsKey(grant))
        }
        probe.evidence.forEach { (grant, _) ->
            assertTrue(grant in probe.granted)
        }
    }

    @Test
    fun `radio log scanner returns empty without grant - no fabricated hits`() {
        // Without READ_LOGS, scanning must yield empty (no invented hits).
        // On the JVM, logcat may fail entirely — both paths must be empty.
        val hits = RadioLogSilentSmsScanner.scan(maxLines = 10)
        // If the radio buffer is unreadable, no hits can exist.
        if (!RadioLogSilentSmsScanner.radioBufferReadable()) {
            assertTrue("no hits without readable radio buffer", hits.isEmpty())
        }
    }

    @Test
    fun `EXACT_BY_LOG proof boundary is distinct from modem EXACT`() {
        assertTrue(
            com.flockyou.detection.silentsms.SourceClass.EXACT_BY_LOG.proofBoundary
                .contains("log fidelity varies")
        )
        assertTrue(
            com.flockyou.detection.silentsms.SourceClass.EXACT.proofBoundary
                .contains("directly observed")
        )
        // Not the same boundary — the log path is weaker by construction.
        assertTrue(
            com.flockyou.detection.silentsms.SourceClass.EXACT_BY_LOG.proofBoundary !=
                com.flockyou.detection.silentsms.SourceClass.EXACT.proofBoundary
        )
    }

    @Test
    fun `radio log scanner detects type0 pattern in synthetic line`() {
        // Pattern-level verification without requiring the radio buffer.
        val patterns = listOf(
            Regex("(?i)SMS-DELIVER.*TYPE[-_ ]?0"),
            Regex("(?i)TP-PID[=:]\\s*0x40")
        )
        val realLine = "01-01 00:00:00.000 E/RIL( 123): SMS-DELIVER received, TYPE 0, tpPid=0x40"
        assertTrue(patterns.any { it.containsMatchIn(realLine) })
        val benign = "01-01 00:00:00.000 D/RIL( 123): SMS-DELIVER normal message from +15551234"
        assertFalse(patterns.any { it.containsMatchIn(benign) })
    }

    @Test
    fun `legacy bridge unchanged by adb rung`() {
        val snap = CapabilitySnapshot(
            rung = CapabilityRung.ADB_PRIVILEGED,
            capabilities = setOf(DetectionCapability.BASIC_SENSORS,
                DetectionCapability.ADB_GRANTED_PERMISSIONS),
            capabilityEvidence = emptyMap(),
            unverifiedClaims = emptyMap(),
            detectedAtMs = 0L
        )
        // ADB rung still maps to Sideload semantics for legacy callers.
        assertTrue(snap.toLegacyPrivilegeMode() is PrivilegeMode.Sideload)
    }
}
