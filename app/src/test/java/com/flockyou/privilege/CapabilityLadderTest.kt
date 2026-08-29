package com.flockyou.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Capability ladder unit tests: rung selection, ordering, fallback,
 * capability verification semantics, and legacy bridge compatibility.
 * Pure decision surfaces — no Android runtime required.
 */
class CapabilityLadderTest {

    // ==================== Rung ordering ====================

    @Test
    fun `ladder ordering is monotonic`() {
        val rungs = CapabilityRung.entries.sortedBy { it.rank }
        assertEquals(
            listOf(CapabilityRung.SIDELOAD, CapabilityRung.ADB_PRIVILEGED,
                CapabilityRung.ROOT_LIBSU, CapabilityRung.MAGISK_COMPANION,
                CapabilityRung.SYSTEM_PRIVAPP, CapabilityRung.OEM_PLATFORM),
            rungs
        )
        assertTrue(CapabilityRung.OEM_PLATFORM.atLeast(CapabilityRung.SIDELOAD))
        assertTrue(CapabilityRung.MAGISK_COMPANION.atLeast(CapabilityRung.ROOT_LIBSU))
        assertFalse(CapabilityRung.SIDELOAD.atLeast(CapabilityRung.ROOT_LIBSU))
    }

    // ==================== Snapshot semantics ====================

    @Test
    fun `sideload snapshot has basic sensors only`() {
        val snap = CapabilitySnapshot(
            rung = CapabilityRung.SIDELOAD,
            capabilities = setOf(DetectionCapability.BASIC_SENSORS),
            capabilityEvidence = mapOf(DetectionCapability.BASIC_SENSORS to "public API"),
            unverifiedClaims = emptyMap(),
            detectedAtMs = 0L
        )
        assertTrue(snap.has(DetectionCapability.BASIC_SENSORS))
        assertFalse(snap.has(DetectionCapability.ROOT_SHELL))
        assertFalse(snap.has(DetectionCapability.MODEM_DIAG_ACCESS))
        assertEquals(null, snap.modemEvidencePath)
    }

    @Test
    fun `modem evidence path prefers highest verified sensor`() {
        val snap = CapabilitySnapshot(
            rung = CapabilityRung.MAGISK_COMPANION,
            capabilities = setOf(
                DetectionCapability.BASIC_SENSORS,
                DetectionCapability.ROOT_SHELL,
                DetectionCapability.MAGISK_COMPANION_IPC,
                DetectionCapability.MODEM_DIAG_ACCESS
            ),
            capabilityEvidence = mapOf(
                DetectionCapability.ROOT_SHELL to "root shell ok",
                DetectionCapability.MAGISK_COMPANION_IPC to "companion ok",
                DetectionCapability.MODEM_DIAG_ACCESS to "diag node ok"
            ),
            unverifiedClaims = emptyMap(),
            detectedAtMs = 0L
        )
        assertEquals("diag node ok", snap.modemEvidencePath)
    }

    @Test
    fun `root without modem diag exposes shell but no modem path`() {
        val snap = CapabilitySnapshot(
            rung = CapabilityRung.ROOT_LIBSU,
            capabilities = setOf(DetectionCapability.BASIC_SENSORS, DetectionCapability.ROOT_SHELL),
            capabilityEvidence = mapOf(DetectionCapability.ROOT_SHELL to "uid=0 via /system/bin/su"),
            unverifiedClaims = mapOf(CapabilityRung.ROOT_LIBSU to listOf("MODEM_DIAG_ACCESS: not found")),
            detectedAtMs = 0L
        )
        assertTrue(snap.has(DetectionCapability.ROOT_SHELL))
        assertFalse(snap.has(DetectionCapability.MODEM_DIAG_ACCESS))
        org.junit.Assert.assertNull(snap.modemEvidencePath)
    }

    @Test
    fun `unverified claims are recorded not silently dropped`() {
        val snap = CapabilitySnapshot(
            rung = CapabilityRung.OEM_PLATFORM,
            capabilities = setOf(DetectionCapability.BASIC_SENSORS,
                DetectionCapability.PRIVILEGED_PHONE_STATE),
            capabilityEvidence = mapOf(DetectionCapability.PRIVILEGED_PHONE_STATE to "platform-signed"),
            unverifiedClaims = mapOf(CapabilityRung.OEM_PLATFORM to
                listOf("MODEM_DIAG_ACCESS: platform-signed but no modem channel probe passed")),
            detectedAtMs = 0L
        )
        assertFalse(snap.has(DetectionCapability.MODEM_DIAG_ACCESS))
        assertTrue(snap.unverifiedClaims[CapabilityRung.OEM_PLATFORM]!!.isNotEmpty())
    }

    // ==================== Evidence labeling ====================

    @Test
    fun `evidence label reflects sensor availability not rung`() {
        val snap = CapabilitySnapshot(
            rung = CapabilityRung.OEM_PLATFORM,
            capabilities = setOf(DetectionCapability.BASIC_SENSORS),
            capabilityEvidence = emptyMap(),
            unverifiedClaims = emptyMap(),
            detectedAtMs = 0L
        )
        // A platform-signed rung WITHOUT a working modem sensor still
        // produces INDIRECT evidence for silent-SMS — capability without
        // sensor does not fabricate EXACT.
        assertEquals("INDIRECT", snap.evidenceLabel(hasExactSensor = false))
        assertEquals("EXACT", snap.evidenceLabel(hasExactSensor = true))
    }

    // ==================== Legacy bridge compatibility ====================

    @Test
    fun `legacy bridge maps platform rung to OEM mode`() {
        val snap = CapabilitySnapshot(
            rung = CapabilityRung.OEM_PLATFORM,
            capabilities = setOf(DetectionCapability.BASIC_SENSORS,
                DetectionCapability.PRIVILEGED_PHONE_STATE),
            capabilityEvidence = emptyMap(),
            unverifiedClaims = emptyMap(),
            detectedAtMs = 0L
        )
        val legacy = snap.toLegacyPrivilegeMode()
        assertTrue(legacy is PrivilegeMode.OEM)
        assertTrue(legacy.isPrivileged)
        assertTrue(legacy.hasPrivilegedPhoneAccess)
    }

    @Test
    fun `legacy bridge maps privapp rung to System mode`() {
        val snap = CapabilitySnapshot(
            rung = CapabilityRung.SYSTEM_PRIVAPP,
            capabilities = setOf(DetectionCapability.BASIC_SENSORS,
                DetectionCapability.CONTINUOUS_BLE, DetectionCapability.REAL_MAC_ADDRESS),
            capabilityEvidence = emptyMap(),
            unverifiedClaims = emptyMap(),
            detectedAtMs = 0L
        )
        val legacy = snap.toLegacyPrivilegeMode()
        assertTrue(legacy is PrivilegeMode.System)
        assertTrue(legacy.canDisableWifiThrottling)
        assertTrue(legacy.hasRealMacAccess)
        assertFalse(legacy.hasPrivilegedPhoneAccess)
    }

    @Test
    fun `legacy bridge maps root rungs to Sideload for compatibility`() {
        for (rung in listOf(CapabilityRung.ROOT_LIBSU, CapabilityRung.MAGISK_COMPANION)) {
            val snap = CapabilitySnapshot(
                rung = rung,
                capabilities = setOf(DetectionCapability.BASIC_SENSORS, DetectionCapability.ROOT_SHELL),
                capabilityEvidence = emptyMap(),
                unverifiedClaims = emptyMap(),
                detectedAtMs = 0L
            )
            // Existing callers see Sideload semantics for root rungs — the
            // ladder's added capabilities are additive, never breaking.
            assertTrue(snap.toLegacyPrivilegeMode() is PrivilegeMode.Sideload)
            assertFalse(snap.toLegacyPrivilegeMode().isPrivileged)
        }
    }
}
