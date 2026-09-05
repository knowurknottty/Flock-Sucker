package com.flockyou.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PublicSafetyVendorSignaturesTest {
    private fun match(ssid: String): DetectionPattern? =
        SsidPatterns.entries.firstOrNull { entry ->
            entry.pattern?.let { Regex(it).matches(ssid) } == true
        }

    @Test
    fun `requested public safety vendor identifiers resolve conservatively`() {
        assertEquals("Flock Safety", match("FlockSafety-123")?.manufacturer)
        assertEquals("Motorola Solutions", match("Vigilant-LPR-01")?.manufacturer)
        assertEquals("Axon Enterprise", match("Axon-Body-4")?.manufacturer)
        assertEquals("Skydio", match("Skydio-X10")?.manufacturer)
        assertEquals("Autel Robotics", match("Autel-EVO-Max")?.manufacturer)
        assertEquals("BRINC", match("BRINC-Lemur-2")?.manufacturer)
        assertEquals("COBAN Technologies / Safe Fleet", match("FOCUS-X2-BWC")?.manufacturer)
        assertNotNull(CameraSignatures.vendorForMac("E0:A7:00:12:34:56"))
    }

    @Test
    fun `generic words do not become surveillance signatures`() {
        assertNull(match("Windows"))
        assertNull(match("Iris"))
        assertNull(match("Gantry"))
        assertNull(match("Site"))
        assertNull(match("X10"))
    }
}
