package com.flockyou.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationTest {
    @Test
    fun `observation preserves raw identity and evidence separately from inference`() {
        val observation = Observation(
            id = "obs-1",
            sessionId = "scan-1",
            timestamp = 1_788_593_035_776L,
            elapsedRealtimeNanos = 42L,
            protocol = ObservationProtocol.BLUETOOTH_LE,
            sourceScanner = "NATIVE_BLE",
            observedIdentifier = "E5:84:82:A2:28:7F",
            identifierKind = ObservationIdentifierKind.BLE_ADDRESS,
            bleAddressType = BleAddressType.RANDOM,
            rssi = -61,
            manufacturerDataJson = "{\"76\":\"1207AABB\"}",
            serviceUuidsJson = "[]",
            rawPayloadSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            rawMetadata = "{\"raw\":true}",
            parserVersion = 1,
            schemaVersion = 1
        )

        assertEquals("E5:84:82:A2:28:7F", observation.observedIdentifier)
        assertEquals(BleAddressType.RANDOM, observation.bleAddressType)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", observation.rawPayloadSha256)
        assertEquals(ObservationDisposition.CAPTURED, observation.disposition)
    }
}