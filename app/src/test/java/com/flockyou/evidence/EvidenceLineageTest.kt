package com.flockyou.evidence

import com.flockyou.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceLineageTest {
    @Test
    fun `compatibility detection and sighting retain source observation id`() {
        val detection = Detection(
            protocol = DetectionProtocol.BLUETOOTH_LE,
            detectionMethod = DetectionMethod.BLE_DEVICE_NAME,
            deviceType = DeviceType.GENERIC_BLE_TRACKER,
            rssi = -65,
            signalStrength = SignalStrength.MEDIUM,
            threatLevel = ThreatLevel.LOW,
            sourceObservationId = "obs-123"
        )
        val sighting = Sighting(
            id = "s-1",
            detectionId = detection.id,
            timestamp = detection.timestamp,
            sequence = 1,
            protocol = detection.protocol.name,
            sourceScanner = "ANDROID_BLE",
            disposition = SightingDisposition.NEW_DEVICE.value(),
            sourceObservationId = detection.sourceObservationId
        )

        assertEquals("obs-123", detection.sourceObservationId)
        assertEquals("obs-123", sighting.sourceObservationId)
    }
}
