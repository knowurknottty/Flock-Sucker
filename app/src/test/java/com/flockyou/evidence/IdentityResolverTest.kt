package com.flockyou.evidence

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdentityResolverTest {
    private val resolver = IdentityResolver()

    @Test
    fun `exact globally administered WiFi BSSID is a canonical match`() {
        val left = detection(protocol = DetectionProtocol.WIFI, mac = "00:30:44:A4:05:BF")
        val right = detection(protocol = DetectionProtocol.WIFI, mac = "00:30:44:a4:05:bf")
        val decision = resolver.resolve(left, right)

        assertEquals(IdentityDecisionClass.MATCH, decision.decision)
        assertEquals("EXACT_STABLE_ADDRESS", decision.ruleId)
        assertEquals(1.0f, decision.score)
    }

    @Test
    fun `exact random-static-compatible BLE address is not sufficient for canonical identity`() {
        val left = detection(protocol = DetectionProtocol.BLUETOOTH_LE, mac = "E5:84:82:A2:28:7F")
        val right = detection(protocol = DetectionProtocol.BLUETOOTH_LE, mac = "E5:84:82:A2:28:7F")
        val decision = resolver.resolve(left, right)

        assertNotEquals(IdentityDecisionClass.MATCH, decision.decision)
        assertEquals(IdentityDecisionClass.POSSIBLY_RELATED, decision.decision)
    }

    @Test
    fun `weak similarity only produces possibly-related`() {
        val left = detection(mac = "10:11:22:33:44:55", name = "Accessory", manufacturer = "Vendor", serviceUuids = "FD6F")
        val right = detection(mac = "20:11:22:33:44:55", name = "Accessory", manufacturer = "Vendor", serviceUuids = "FD6F")
        val decision = resolver.resolve(left, right)

        assertEquals(IdentityDecisionClass.POSSIBLY_RELATED, decision.decision)
        assertNotEquals("EXACT_STABLE_ADDRESS", decision.ruleId)
    }

    @Test
    fun `different protocols are distinct without cross-protocol stable evidence`() {
        val left = detection(protocol = DetectionProtocol.WIFI, mac = "00:30:44:A4:05:BF")
        val right = detection(protocol = DetectionProtocol.BLUETOOTH_LE, mac = "00:30:44:A4:05:BF")

        assertEquals(IdentityDecisionClass.DISTINCT, resolver.resolve(left, right).decision)
    }

    private fun detection(
        protocol: DetectionProtocol = DetectionProtocol.BLUETOOTH_LE,
        mac: String? = null,
        name: String? = null,
        manufacturer: String? = null,
        serviceUuids: String? = null
    ) = Detection(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        protocol = protocol,
        detectionMethod = DetectionMethod.SSID_PATTERN,
        deviceType = DeviceType.GENERIC_BLE_TRACKER,
        rssi = -60,
        signalStrength = SignalStrength.GOOD,
        threatLevel = ThreatLevel.LOW,
        macAddress = mac,
        deviceName = name,
        manufacturer = manufacturer,
        serviceUuids = serviceUuids
    )
}
