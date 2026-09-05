package com.flockyou.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleTrackerEvidenceClassifierTest {
    @Test
    fun `Apple 07 is proximity pairing not AirTag`() {
        val result = BleTrackerEvidenceClassifier.classify(
            manufacturerData = mapOf(0x004C to "0719010E20"),
            serviceUuids = emptyList()
        )

        assertEquals(BleEvidenceKind.APPLE_PROXIMITY_PAIRING, result.kind)
        assertFalse(result.trackerEvidence)
        assertNull(result.suggestedDeviceType)
    }

    @Test
    fun `Apple 12 is Find My network evidence but not exact AirTag identity`() {
        val result = BleTrackerEvidenceClassifier.classify(
            manufacturerData = mapOf(0x004C to "1219AABBCCDD"),
            serviceUuids = emptyList()
        )

        assertEquals(BleEvidenceKind.APPLE_FIND_MY_NETWORK, result.kind)
        assertTrue(result.trackerEvidence)
        assertEquals("Apple Find My network device", result.displayName)
        assertNull(result.suggestedDeviceType)
    }

    @Test
    fun `Apple Find My service UUID is network evidence not exact AirTag`() {
        val result = BleTrackerEvidenceClassifier.classify(
            manufacturerData = emptyMap(),
            serviceUuids = listOf("7DFC9000-7D1C-4951-86AA-8D9728F8D66C")
        )

        assertEquals(BleEvidenceKind.APPLE_FIND_MY_NETWORK, result.kind)
        assertTrue(result.trackerEvidence)
        assertFalse(result.exactProductFamily)
        assertNull(result.suggestedDeviceType)
    }

    @Test
    fun `Samsung manufacturer id alone is vendor evidence not SmartTag`() {
        val result = BleTrackerEvidenceClassifier.classify(
            manufacturerData = mapOf(0x0075 to "42040180665CC1D7737B3B"),
            serviceUuids = emptyList()
        )

        assertEquals(BleEvidenceKind.SAMSUNG_VENDOR_ADVERTISEMENT, result.kind)
        assertFalse(result.trackerEvidence)
        assertNull(result.suggestedDeviceType)
    }

    @Test
    fun `Find My frame does not count toward Apple popup spam`() {
        assertFalse(BleTrackerEvidenceClassifier.isApplePopupEligible("1219AABBCCDD"))
        assertTrue(BleTrackerEvidenceClassifier.isApplePopupEligible("0719010E20"))
    }

    @Test
    fun `FD5A is strong Samsung SmartTag protocol evidence`() {
        val result = BleTrackerEvidenceClassifier.classify(
            manufacturerData = mapOf(0x0075 to "0102"),
            serviceUuids = listOf("0000FD5A-0000-1000-8000-00805F9B34FB")
        )

        assertEquals(BleEvidenceKind.SAMSUNG_SMARTTAG_SERVICE, result.kind)
        assertTrue(result.trackerEvidence)
        assertTrue(result.exactProductFamily)
    }

    @Test
    fun `FD69 is Samsung offline finding evidence not exact SmartTag`() {
        val result = BleTrackerEvidenceClassifier.classify(
            manufacturerData = emptyMap(),
            serviceUuids = listOf("0000FD69-0000-1000-8000-00805F9B34FB")
        )

        assertEquals(BleEvidenceKind.SAMSUNG_OFFLINE_FINDING, result.kind)
        assertTrue(result.trackerEvidence)
        assertFalse(result.exactProductFamily)
    }
}
