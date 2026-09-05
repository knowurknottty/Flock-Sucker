package com.flockyou.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage tests for the known camera signature registry: vendor OUIs,
 * default SSIDs, and default network ports.
 */
class CameraSignaturesTest {

    @Test
    fun `major camera vendors resolve by OUI`() {
        // Hikvision
        assertEquals("Hikvision", CameraSignatures.vendorForMac("B4:A3:82:11:22:33")?.vendor)
        assertEquals("Hikvision", CameraSignatures.vendorForMac("44:19:B6:AA:BB:CC")?.vendor)
        // Dahua
        assertEquals("Dahua", CameraSignatures.vendorForMac("E0:50:8B:01:02:03")?.vendor)
        // Axis
        assertEquals("Axis Communications", CameraSignatures.vendorForMac("00:40:8C:DE:AD:BE")?.vendor)
        // Reolink
        assertEquals("Reolink", CameraSignatures.vendorForMac("B0:B5:49:00:00:01")?.vendor)
        // Verkada
        assertEquals("Verkada", CameraSignatures.vendorForMac("E0:A7:00:12:34:56")?.vendor)
        assertNull("Apple OUI must never be attributed to Verkada", CameraSignatures.vendorForMac("8C:85:90:12:34:56"))
        assertNull("Huawei OUI must never be attributed to Verkada", CameraSignatures.vendorForMac("64:16:F0:12:34:56"))
        // Ubiquiti UniFi Protect
        assertEquals("Ubiquiti UniFi Protect", CameraSignatures.vendorForMac("78:8A:20:00:00:99")?.vendor)
    }

    @Test
    fun `oui lookup accepts raw oui without trailing octets`() {
        assertEquals("Hikvision", CameraSignatures.vendorForMac("B4:A3:82")?.vendor)
        assertEquals("Dahua", CameraSignatures.vendorForMac("e0:50:8b")?.vendor) // case-insensitive
        assertEquals("Dahua", CameraSignatures.vendorForMac("E0-50-8B")?.vendor) // dash format
    }

    @Test
    fun `unknown or locally administered macs do not resolve`() {
        assertNull(CameraSignatures.vendorForMac("00:11:22:33:44:55"))
        assertNull(CameraSignatures.vendorForMac(""))
    }

    @Test
    fun `camera vendor default ssids resolve`() {
        assertEquals("Hikvision", CameraSignatures.vendorForSsid("HIKVision-Cam-1234"))
        assertEquals("Hikvision", CameraSignatures.vendorForSsid("hik_1234"))
        assertEquals("Dahua", CameraSignatures.vendorForSsid("DH-Camera"))
        assertEquals("Reolink", CameraSignatures.vendorForSsid("Reolink-4E2A"))
        assertEquals("Ezviz (Hikvision consumer)", CameraSignatures.vendorForSsid("EZVIZ_12345"))
        assertEquals("Wyze", CameraSignatures.vendorForSsid("WyzeCam-AB12"))
        assertEquals("Ring (Amazon)", CameraSignatures.vendorForSsid("Ring-Setup-6F3A"))
        assertEquals("Ubiquiti UniFi Protect", CameraSignatures.vendorForSsid("UniFi-Protect-Cam-01"))
    }

    @Test
    fun `non-camera ssids do not resolve`() {
        assertNull(CameraSignatures.vendorForSsid(""))
        assertNull(CameraSignatures.vendorForSsid("HomeNetwork_5G"))
        assertNull(CameraSignatures.vendorForSsid("Starbucks WiFi"))
        assertNull(CameraSignatures.vendorForSsid("NETGEAR48"))
    }

    @Test
    fun `default port registry contains the well-known camera ports`() {
        val ports = CameraSignatures.allDefaultPorts
        for (p in listOf(80, 443, 554, 8000, 37777, 37778, 8899, 34567, 7443)) {
            assertTrue("expected port $p in registry", p in ports)
        }
    }

    @Test
    fun `port to vendor mapping keeps provenance`() {
        val hik = CameraSignatures.portToVendors[8000] ?: emptyList()
        assertTrue(hik.any { it.contains("Hikvision") })
        val dahua = CameraSignatures.portToVendors[37777] ?: emptyList()
        assertTrue(dahua.any { it.contains("Dahua") })
        val rtsp = CameraSignatures.portToVendors[554] ?: emptyList()
        assertTrue("554 should map to many vendors", rtsp.size >= 10)
    }

    @Test
    fun `detection patterns expose default ports for a vendor`() {
        assertEquals(listOf(80, 443, 554, 8000, 8443, 9010),
            DetectionPatterns.cameraDefaultPortsForVendor("Hikvision"))
        assertTrue(DetectionPatterns.cameraDefaultPortsForVendor("Nonexistent").isEmpty())
    }

    @Test
    fun `all camera vendor OUIs are resolvable through DetectionPatterns mac prefix or vendor registry`() {
        // Every CameraSignatures OUI should either resolve through the generic
        // OUI manufacturer map / macPrefixes, or at minimum through the vendor
        // registry itself — no dead entries.
        for (vendor in CameraSignatures.vendors) {
            for (oui in vendor.ouiPrefixes) {
                val resolved = CameraSignatures.vendorForMac(oui)
                assertNotNull("OUI $oui (${vendor.vendor}) dead in registry", resolved)
            }
        }
    }

    @Test
    fun `registry OUIs are unique across vendors - attribution never order-dependent`() {
        val seen = mutableMapOf<String, String>()
        for (vendor in CameraSignatures.vendors) {
            for (oui in vendor.ouiPrefixes) {
                val key = oui.uppercase()
                val previous = seen[key]
                assertNull("OUI $key claimed by both '${previous}' and '${vendor.vendor}'", previous)
                seen[key] = vendor.vendor
            }
        }
        // Multi-vendor resolution must agree with single-vendor resolution.
        for ((oui, vendor) in seen) {
            val all = CameraSignatures.vendorsForMac(oui)
            assertEquals(1, all.size)
            assertEquals(vendor, all.first().vendor)
        }
    }

    @Test
    fun `new camera OUIs are wired into DetectionPatterns macPrefixes`() {
        // Spot-check the newly added MacPrefix entries resolve to camera types.
        val checks = mapOf(
            "44:47:CC" to DeviceType.HIDDEN_CAMERA,
            "B8:A4:4F" to DeviceType.CCTV_CAMERA,
            "B0:B5:49" to DeviceType.WYZE_CAMERA,
            "E0:A7:00" to DeviceType.CCTV_CAMERA,
            "0C:80:63" to DeviceType.RING_DOORBELL,
            "58:2D:34" to DeviceType.BLINK_CAMERA
        )
        for ((mac, expectedType) in checks) {
            val mp = DetectionPatterns.matchMacPrefix("$mac:11:22:33")
            assertNotNull("MacPrefix $mac missing", mp)
            assertEquals(expectedType, mp?.deviceType)
        }
    }

    @Test
    fun `ssid pattern registry includes camera vendor default SSIDs`() {
        val ssids = SsidPatterns.entries.mapNotNull { e ->
            e.pattern?.let { Regex(it) to e }
        }
        fun matches(ssid: String) = ssids.any { it.first.matches(ssid) }

        assertTrue(matches("HIKVISION-CAM"))
        assertTrue(matches("Dahua-IPC"))
        assertTrue(matches("Reolink-8F2C"))
        assertTrue(matches("EZVIZ_001"))
        assertTrue(matches("Tapo-C200"))
        assertTrue(matches("UniFi-Protect-01"))
        assertTrue(matches("ESP32-CAM"))
        // Pre-existing coverage preserved
        assertTrue(matches("IPCam-ABCDEF"))
        assertTrue(matches("spy-cam"))
    }
}
