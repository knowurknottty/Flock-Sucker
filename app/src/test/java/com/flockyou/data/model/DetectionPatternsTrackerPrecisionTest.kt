package com.flockyou.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class DetectionPatternsTrackerPrecisionTest {
    @Test
    fun `Apple proximity pairing is not suggested as AirTag`() {
        val analysis = DetectionPatterns.analyzeUnknownDevice(
            macAddress = "C6:7B:70:17:CB:E0",
            deviceName = null,
            serviceUuids = emptyList(),
            manufacturerData = mapOf(0x004C to "0719010E20"),
            rssi = -60,
            advertisingRate = 1f
        )
        assertNull(analysis.suggestedDeviceType)
    }

    @Test
    fun `generic Samsung manufacturer advertisement is not suggested as SmartTag`() {
        val analysis = DetectionPatterns.analyzeUnknownDevice(
            macAddress = "5C:C1:D7:73:7B:3B",
            deviceName = null,
            serviceUuids = emptyList(),
            manufacturerData = mapOf(0x0075 to "42040180665CC1D7737B3B"),
            rssi = -60,
            advertisingRate = 1f
        )
        assertNull(analysis.suggestedDeviceType)
    }

    @Test
    fun `Find My frame suggests generic tracker rather than exact AirTag`() {
        val analysis = DetectionPatterns.analyzeUnknownDevice(
            macAddress = "C6:7B:70:17:CB:E0",
            deviceName = null,
            serviceUuids = emptyList(),
            manufacturerData = mapOf(0x004C to "1219AABBCCDD"),
            rssi = -60,
            advertisingRate = 1f
        )
        assertEquals(DeviceType.GENERIC_BLE_TRACKER, analysis.suggestedDeviceType)
    }

    @Test
    fun `FD5A remains exact SmartTag family suggestion`() {
        val analysis = DetectionPatterns.analyzeUnknownDevice(
            macAddress = "A4:57:A0:9C:57:DD",
            deviceName = null,
            serviceUuids = listOf(UUID.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")),
            manufacturerData = emptyMap(),
            rssi = -60,
            advertisingRate = 1f
        )
        assertEquals(DeviceType.SAMSUNG_SMARTTAG, analysis.suggestedDeviceType)
    }
}
