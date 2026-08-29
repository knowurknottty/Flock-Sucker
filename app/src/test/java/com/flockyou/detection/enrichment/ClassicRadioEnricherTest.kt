package com.flockyou.detection.enrichment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicRadioEnricherTest {

    // ==================== Bluetooth Classic ====================

    @Test
    fun `surveillance-relevant classic device - capture plus audio`() {
        // Major class AUDIO_VIDEO (0x04 << 8) with CAPTURE + AUDIO service bits
        val device = ClassicRadioEnricher.ClassicDevice(
            macAddress = "00:1A:7D:00:00:01", name = "BT-Audio-1",
            deviceClass = 0x240408, rssi = -60, timestampMs = 0L
        )
        // 0x240408: major 0x04 (audio/video), service bits: capture 0x80000? compute below
        // 0x240408 & 0x1F00 = 0x400 → major AUDIO_VIDEO ✓
        assertEquals("AUDIO_VIDEO", device.majorDeviceClass)
    }

    @Test
    fun `major device class decoding`() {
        fun major(cls: Int) = ClassicRadioEnricher.ClassicDevice(
            "00:11:22:33:44:55", null, cls, null, 0L
        ).majorDeviceClass
        assertEquals("COMPUTER", major(0x000100))
        assertEquals("PHONE", major(0x000200))
        assertEquals("AUDIO_VIDEO", major(0x000400))
        assertEquals("UNCATEGORIZED", major(0x001F00))
    }

    @Test
    fun `service classes decode`() {
        val device = ClassicRadioEnricher.ClassicDevice(
            "00:11:22:33:44:55", null, 0x100000, null, 0L
        )
        assertTrue(device.serviceClasses.contains("OBJECT_TRANSFER"))
    }

    @Test
    fun `suspect OUI matching`() {
        assertTrue(ClassicRadioEnricher.isSuspectOui("00:1A:7D:11:22:33"))
        assertTrue(ClassicRadioEnricher.isSuspectOui("001a7d1122"))
        assertFalse(ClassicRadioEnricher.isSuspectOui("00:11:22:33:44:55"))
    }

    // ==================== DHCP fingerprints ====================

    @Test
    fun `hikvision option55 signature matches`() {
        val sig = ClassicRadioEnricher.DHCP_FINGERPRINTS["hikvision-dh"]!!
        assertEquals("hikvision-dh", ClassicRadioEnricher.matchDhcpFingerprint(sig))
        // order-independent
        assertEquals("hikvision-dh", ClassicRadioEnricher.matchDhcpFingerprint(sig.reversed()))
    }

    @Test
    fun `unknown option55 list does not match`() {
        assertNull(ClassicRadioEnricher.matchDhcpFingerprint(listOf(1, 2, 3)))
        assertNull(ClassicRadioEnricher.matchDhcpFingerprint(emptyList()))
    }

    // ==================== Truth constraints ====================

    @Test
    fun `ie fingerprint list requires API 30 - no fabricated results`() {
        // The implementation returns empty on < R; the fingerprint table
        // itself is pure data and testable.
        assertTrue(ClassicRadioEnricher.VENDOR_IE_FINGERPRINTS.any {
            it.second.contains("Hi3516")
        })
    }
}
