package com.flockyou.evidence

import com.flockyou.data.model.Observation

/** Bounded forensic line for packet-level BLE diagnosis in logcat/export tooling. */
object BleForensicSummary {
    fun format(
        observation: Observation,
        evidence: BleTrackerEvidence,
        ouiVendor: String? = null
    ): String = buildString {
        append("observation=${observation.id}")
        append(" session=${observation.sessionId}")
        append(" mac=${observation.observedIdentifier ?: "unknown"}")
        append(" addressType=${observation.bleAddressType}")
        append(" rssi=${observation.rssi ?: "unknown"}")
        append(" kind=${evidence.kind}")
        append(" tracker=${evidence.trackerEvidence}")
        append(" exact=${evidence.exactProductFamily}")
        ouiVendor?.let { append(" ouiVendor=$it") }
        append(" manufacturerData=${observation.manufacturerDataJson ?: "{}"}")
        append(" serviceUuids=${observation.serviceUuidsJson ?: "[]"}")
        append(" serviceData=${observation.serviceDataJson ?: "{}"}")
        append(" sha256=${observation.rawPayloadSha256}")
    }
}
