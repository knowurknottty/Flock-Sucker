package com.flockyou.detection

import android.content.Context
import com.flockyou.data.model.DeviceType
import com.flockyou.detection.handler.BleDetectionContext
import com.flockyou.detection.handler.BleDetectionHandler
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleDetectionHandlerPrecisionTest {
    @Test
    fun `Apple proximity pairing does not become AirTag detection`() {
        val result = handler().handleDetection(context(mapOf(0x004C to "0719010E20")))
        assertNull(result)
    }

    @Test
    fun `Samsung company id alone does not become SmartTag detection`() {
        val result = handler().handleDetection(context(mapOf(0x0075 to "42040180665CC1D7737B3B")))
        assertNull(result)
    }

    @Test
    fun `raw tracker detail preserves service-data payload bytes`() {
        val result = handler().handleDetection(
            BleDetectionContext(
                macAddress = "A4:57:A0:9C:57:DD",
                deviceName = null,
                rssi = -55,
                serviceUuids = listOf(java.util.UUID.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")),
                manufacturerData = mapOf(0x0075 to "0102AABB"),
                serviceData = mapOf("0000FD5A-0000-1000-8000-00805F9B34FB" to "CAFE"),
                advertisingRate = 1f
            )
        )

        val raw = result?.detection?.rawData.orEmpty()
        org.junit.Assert.assertTrue(raw.contains("Service Data (1):"))
        org.junit.Assert.assertTrue(raw.contains("CAFE"))
        org.junit.Assert.assertTrue(raw.contains("0x0075: 0102AABB"))
    }

    @Test
    fun `Apple Find My frame is generic tracker evidence not exact AirTag`() {
        val result = handler().handleDetection(context(mapOf(0x004C to "1219AABBCCDD")))
        assertEquals(DeviceType.GENERIC_BLE_TRACKER, result?.detection?.deviceType)
    }

    private fun handler() = BleDetectionHandler(mockk<Context>(relaxed = true))

    private fun context(manufacturerData: Map<Int, String>) = BleDetectionContext(
        macAddress = "C6:7B:70:17:CB:E0",
        deviceName = null,
        rssi = -55,
        serviceUuids = emptyList(),
        manufacturerData = manufacturerData,
        advertisingRate = 1f
    )
}
