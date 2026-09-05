package com.flockyou.evidence

import com.flockyou.data.model.BleAddressType
import com.flockyou.data.model.Observation
import com.flockyou.data.model.ObservationIdentifierKind
import com.flockyou.data.model.ObservationProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleForensicSummaryTest {
    @Test
    fun `summary exposes packet evidence and protocol verdict without raw payload omission`() {
        val observation = Observation(
            id = "obs-123",
            sessionId = "session-9",
            timestamp = 1234L,
            protocol = ObservationProtocol.BLUETOOTH_LE,
            sourceScanner = "ANDROID_BLE",
            observedIdentifier = "A4:57:A0:9C:57:DD",
            identifierKind = ObservationIdentifierKind.BLE_ADDRESS,
            bleAddressType = BleAddressType.PUBLIC,
            rssi = -58,
            manufacturerDataJson = "{\"117\":\"0102AABB\"}",
            serviceUuidsJson = "[\"0000FD5A-0000-1000-8000-00805F9B34FB\"]",
            serviceDataJson = "{\"0000FD5A-0000-1000-8000-00805F9B34FB\":\"CAFE\"}",
            rawPayloadSha256 = "a".repeat(64),
            parserVersion = 1,
            schemaVersion = 1
        )
        val evidence = BleTrackerEvidenceClassifier.classify(
            mapOf(0x0075 to "0102AABB"),
            listOf("0000FD5A-0000-1000-8000-00805F9B34FB")
        )

        val summary = BleForensicSummary.format(observation, evidence, "SAMJIN Co., Ltd.")
        assertTrue(summary.contains("observation=obs-123"))
        assertTrue(summary.contains("kind=SAMSUNG_SMARTTAG_SERVICE"))
        assertTrue(summary.contains("ouiVendor=SAMJIN Co., Ltd."))
        assertTrue(summary.contains("manufacturerData={\"117\":\"0102AABB\"}"))
        assertTrue(summary.contains("serviceData={\"0000FD5A-0000-1000-8000-00805F9B34FB\":\"CAFE\"}"))
        assertTrue(summary.contains("sha256=${"a".repeat(64)}"))
        assertFalse(summary.contains("exact=unknown"))
    }
}
