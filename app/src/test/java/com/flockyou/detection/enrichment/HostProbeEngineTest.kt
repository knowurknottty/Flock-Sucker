package com.flockyou.detection.enrichment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the evidence-enrichment engine: TTL OS-family inference,
 * MAC cross-reference, and port-probe list invariants. Network probing
 * itself requires a device; the pure decision surfaces are unit-tested here.
 */
class HostProbeEngineTest {

    @Test
    fun `ttl 64-class maps to unix family`() {
        assertEquals("Unix-family (Linux/Android/macOS)", HostProbeEngine.osFamilyFromTtl(64))
        assertEquals("Unix-family (Linux/Android/macOS)", HostProbeEngine.osFamilyFromTtl(60))
    }

    @Test
    fun `ttl 128-class maps to windows family`() {
        assertEquals("Windows-family", HostProbeEngine.osFamilyFromTtl(128))
        assertEquals("Windows-family", HostProbeEngine.osFamilyFromTtl(125))
    }

    @Test
    fun `ttl 255-class maps to appliance`() {
        assertEquals("Network appliance / embedded RTOS", HostProbeEngine.osFamilyFromTtl(255))
        assertEquals("Network appliance / embedded RTOS", HostProbeEngine.osFamilyFromTtl(250))
    }

    @Test
    fun `invalid or null ttl yields no inference`() {
        assertNull(HostProbeEngine.osFamilyFromTtl(null))
        assertNull(HostProbeEngine.osFamilyFromTtl(0))
        assertNull(HostProbeEngine.osFamilyFromTtl(300))
        assertNull(HostProbeEngine.osFamilyFromTtl(-5))
    }

    @Test
    fun `surveillance-class ports are in the probe list`() {
        val ports = HostProbeEngine.PROBE_PORTS
        for (p in listOf(554, 8000, 37777, 34567, 8899, 7443)) {
            assertTrue("expected $p in probe list", p in ports)
        }
    }

    @Test
    fun `mac intelligence resolves camera vendor and its ports`() {
        val intel = HostProbeEngine.macIntelligence("B4:A3:82:11:22:33")
        assertEquals("B4:A3:82", intel?.oui)
        assertEquals("Hikvision", intel?.cameraVendor)
        assertTrue(intel?.cameraPorts?.contains(8000) == true)
    }

    @Test
    fun `mac intelligence accepts dash format and lowercase`() {
        assertEquals(
            HostProbeEngine.macIntelligence("E0:50:8B:AA:BB:CC")?.cameraVendor,
            HostProbeEngine.macIntelligence("e0-50-8b-aa-bb-cc")?.cameraVendor
        )
        assertEquals("Dahua", HostProbeEngine.macIntelligence("E0-50-8B-AA-BB-CC")?.cameraVendor)
    }

    @Test
    fun `unknown mac yields null intelligence`() {
        assertNull(HostProbeEngine.macIntelligence("00:11:22:33:44:55"))
        assertNull(HostProbeEngine.macIntelligence(""))
        assertNull(HostProbeEngine.macIntelligence("B4"))
    }

    @Test
    fun `ieee vendor passes through when provided`() {
        val intel = HostProbeEngine.macIntelligence("B4:A3:82:00:00:01", ieeeVendor = "Hangzhou Hikvision Digital Technology")
        assertEquals("Hangzhou Hikvision Digital Technology", intel?.ieeeVendor)
        assertEquals("Hikvision", intel?.cameraVendor)
    }
}
