package com.flockyou.evidence

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RemoteIdEvidenceDetectorTest {
    @Test
    fun `BLE FFFA OpenDroneID service data is exact remote id evidence`() {
        val message = basicId("USS-Enterprise")
        val payload = byteArrayOf(0x0D, 0x2A) + message
        val evidence = RemoteIdEvidenceDetector.fromBleServiceData(
            mapOf("0000fffa-0000-1000-8000-00805f9b34fb" to payload)
        )
        assertNotNull(evidence)
        assertEquals(RemoteIdTransport.BLUETOOTH, evidence!!.transport)
        assertTrue(RemoteIdMessageType.BASIC_ID in evidence.messageTypes)
        assertEquals(listOf("USS-Enterprise"), evidence.uasIds)
        assertEquals(2, evidence.protocolVersion)
    }

    @Test
    fun `BLE FFFA without OpenDroneID application code is rejected`() {
        val payload = byteArrayOf(0x0C, 0x00) + basicId("NOT-A-DRONE")
        assertNull(RemoteIdEvidenceDetector.fromBleServiceData(
            mapOf("0000fffa-0000-1000-8000-00805f9b34fb" to payload)
        ))
    }

    @Test
    fun `WiFi vendor IE OpenDroneID pack extracts basic id and drone location`() {
        val basic = basicId("RID-TEST-123")
        val location = location(34.7701234, -86.4205678)
        val pack = byteArrayOf(0xF2.toByte(), 0x19, 0x02) + basic + location
        val ie = WifiInformationElementEvidence(
            id = 221,
            idExt = 0,
            bytes = byteArrayOf(0xFA.toByte(), 0x0B, 0xBC.toByte(), 0x0D, 0x55) + pack
        )
        val evidence = RemoteIdEvidenceDetector.fromWifiInformationElements(listOf(ie))
        assertNotNull(evidence)
        assertEquals(RemoteIdTransport.WIFI_BEACON, evidence!!.transport)
        assertEquals(2, evidence.messageCount)
        assertTrue(RemoteIdMessageType.LOCATION in evidence.messageTypes)
        assertEquals("RID-TEST-123", evidence.uasIds.single())
        assertEquals(34.7701234, evidence.droneLatitude!!, 0.0000001)
        assertEquals(-86.4205678, evidence.droneLongitude!!, 0.0000001)
    }

    @Test
    fun `lookalike WiFi vendor IE is not remote id`() {
        val ie = WifiInformationElementEvidence(221, 0,
            byteArrayOf(0xFA.toByte(), 0x0B, 0xBD.toByte(), 0x0D, 0x00, 0xF2.toByte(), 0x19, 0x00))
        assertNull(RemoteIdEvidenceDetector.fromWifiInformationElements(listOf(ie)))
    }

    private fun basicId(id: String): ByteArray {
        val out = ByteArray(25)
        out[0] = 0x02 // Basic ID, protocol v2
        out[1] = 0x14 // serial-number ID type, helicopter/multirotor UA type
        id.encodeToByteArray().take(20).forEachIndexed { i, b -> out[2 + i] = b }
        return out
    }

    private fun location(lat: Double, lon: Double): ByteArray {
        val out = ByteArray(25)
        out[0] = 0x12 // Location/Vector, protocol v2
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(5, (lat * 10_000_000.0).toInt())
            putInt(9, (lon * 10_000_000.0).toInt())
        }
        return out
    }
}
