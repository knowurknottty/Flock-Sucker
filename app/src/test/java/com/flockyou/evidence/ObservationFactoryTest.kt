package com.flockyou.evidence

import com.flockyou.data.model.BleAddressType
import com.flockyou.data.model.ObservationIdentifierKind
import com.flockyou.data.model.ObservationProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationFactoryTest {
    @Test
    fun `BLE normalization preserves source identity payload and radio metadata`() {
        val input = BleObservationInput(
            observationId = "ble-1",
            sessionId = "session-1",
            timestamp = 1_788_593_035_776L,
            elapsedRealtimeNanos = 123_456L,
            sourceScanner = "NATIVE_BLE",
            observedAddress = "e5:84:82:a2:28:7f",
            addressType = BleAddressType.RANDOM,
            deviceName = "Accessory",
            rssi = -61,
            txPower = -8,
            primaryPhy = 1,
            secondaryPhy = 2,
            advertisingSid = 7,
            periodicAdvertisingInterval = 80,
            manufacturerData = linkedMapOf(117 to byteArrayOf(0x42, 0x04), 76 to byteArrayOf(0x12, 0x07)),
            serviceUuids = listOf("FEAA", "FD6F"),            serviceData = linkedMapOf("FD6F" to byteArrayOf(0x01, 0x02)),
            rawScanRecord = byteArrayOf(0x02, 0x01, 0x06),
            latitude = 34.77548,
            longitude = -86.42471,
            accuracyMeters = 4.5f
        )

        val observation = ObservationFactory.fromBle(input)

        assertEquals(ObservationProtocol.BLUETOOTH_LE, observation.protocol)
        assertEquals("E5:84:82:A2:28:7F", observation.observedIdentifier)
        assertEquals(ObservationIdentifierKind.BLE_ADDRESS, observation.identifierKind)
        assertEquals(BleAddressType.RANDOM, observation.bleAddressType)
        assertEquals(-8, observation.txPower)
        assertEquals(1, observation.primaryPhy)
        assertEquals(2, observation.secondaryPhy)
        assertTrue(observation.manufacturerDataJson!!.contains("\"76\":\"1207\""))
        assertTrue(observation.manufacturerDataJson!!.contains("\"117\":\"4204\""))
        assertTrue(observation.serviceDataJson!!.contains("\"FD6F\":\"0102\""))
        assertEquals(64, observation.rawPayloadSha256.length)
    }

    @Test
    fun `BLE digest is deterministic across map and UUID ordering`() {
        val first = minimalBle(
            manufacturerData = linkedMapOf(117 to byteArrayOf(0x42), 76 to byteArrayOf(0x12)),
            serviceUuids = listOf("FEAA", "FD6F")
        )
        val second = minimalBle(
            manufacturerData = linkedMapOf(76 to byteArrayOf(0x12), 117 to byteArrayOf(0x42)),
            serviceUuids = listOf("FD6F", "FEAA")
        )
        assertEquals(
            ObservationFactory.fromBle(first).rawPayloadSha256,
            ObservationFactory.fromBle(second).rawPayloadSha256
        )
        assertNotEquals(
            ObservationFactory.fromBle(first).rawPayloadSha256,
            ObservationFactory.fromBle(first.copy(rawScanRecord = byteArrayOf(0x01))).rawPayloadSha256
        )
    }

    @Test
    fun `WiFi normalization preserves BSSID RF and information elements`() {
        val observation = ObservationFactory.fromWifi(
            WifiObservationInput(
                observationId = "wifi-1",
                sessionId = "session-1",
                timestamp = 1_788_593_035_900L,
                sourceScanner = "WIFI_MANAGER",
                bssid = "00:30:44:a4:05:bf",
                ssid = "E110-5be",
                rssi = -78,
                frequencyMhz = 5180,
                channelWidth = 2,
                informationElements = listOf(
                    WifiInformationElementEvidence(0, 0, byteArrayOf(0x01, 0x02)),
                    WifiInformationElementEvidence(221, 0, byteArrayOf(0x00, 0x30, 0x44))
                )
            )
        )

        assertEquals(ObservationProtocol.WIFI, observation.protocol)
        assertEquals("00:30:44:A4:05:BF", observation.observedIdentifier)
        assertEquals(ObservationIdentifierKind.WIFI_BSSID, observation.identifierKind)
        assertEquals(5180, observation.frequencyMhz)
        assertTrue(observation.informationElementsJson!!.contains("003044"))
    }

    private fun minimalBle(
        manufacturerData: Map<Int, ByteArray>,
        serviceUuids: List<String>
    ) = BleObservationInput(
        observationId = "ble-min",
        sessionId = "session-1",
        timestamp = 1L,
        elapsedRealtimeNanos = 1L,
        sourceScanner = "NATIVE_BLE",
        observedAddress = "C5:9D:5F:FF:5D:CD",
        addressType = BleAddressType.RANDOM,
        rssi = -70,
        manufacturerData = manufacturerData,
        serviceUuids = serviceUuids,
        rawScanRecord = byteArrayOf(0x02, 0x01, 0x06)
    )
}
