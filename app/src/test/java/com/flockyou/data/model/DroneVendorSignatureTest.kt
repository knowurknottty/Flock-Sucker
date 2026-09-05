package com.flockyou.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DroneVendorSignatureTest {
    private fun match(ssid: String): DetectionPattern? =
        SsidPatterns.entries.firstOrNull { entry ->
            entry.pattern?.let { Regex(it).matches(ssid) } == true
        }

    @Test
    fun `public safety drone model identifiers resolve conservatively`() {
        assertEquals("Skydio", match("Skydio-X10")?.manufacturer)
        assertEquals("Skydio", match("Skydio-X10D-unit7")?.manufacturer)
        assertEquals("Autel Robotics", match("Autel-EVO-Max")?.manufacturer)
        assertEquals("BRINC", match("BRINC-Lemur-2")?.manufacturer)
        assertEquals(DeviceType.DRONE, match("BRINC-Responder-01")?.deviceType)
    }

    @Test
    fun `generic model-like words are not drone signatures`() {
        assertNull(match("X10"))
        assertNull(match("Responder"))
        assertNull(match("Max"))
        assertNull(match("Drone"))
    }
}
