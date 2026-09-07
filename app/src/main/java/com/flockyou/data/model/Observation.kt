package com.flockyou.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Immutable scanner-ingress evidence. One row represents one radio observation
 * before classification, deduplication, identity resolution, or threat scoring.
 */
@Entity(
    tableName = "observations",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"]),
        Index(value = ["protocol", "timestamp"]),
        Index(value = ["observedIdentifier"]),
        Index(value = ["rawPayloadSha256"])
    ]
)
data class Observation(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val elapsedRealtimeNanos: Long? = null,
    val protocol: ObservationProtocol,
    val sourceScanner: String,
    val scannerHealthGeneration: Long = 0,
    val observedIdentifier: String? = null,
    val identifierKind: ObservationIdentifierKind = ObservationIdentifierKind.NONE,
    val bleAddressType: BleAddressType? = null,
    val deviceName: String? = null,
    val ssid: String? = null,
    val rssi: Int? = null,
    val txPower: Int? = null,
    val primaryPhy: Int? = null,
    val secondaryPhy: Int? = null,
    val advertisingSid: Int? = null,
    val periodicAdvertisingInterval: Int? = null,    val frequencyMhz: Int? = null,
    val channelWidth: Int? = null,
    val manufacturerDataJson: String? = null,
    val serviceUuidsJson: String? = null,
    val serviceDataJson: String? = null,
    val informationElementsJson: String? = null,
    val rawPayloadSha256: String,
    val rawMetadata: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val parserVersion: Int,
    val schemaVersion: Int,
    val disposition: ObservationDisposition = ObservationDisposition.CAPTURED
) {
    init {
        require(id.isNotBlank()) { "Observation id must not be blank" }
        require(sessionId.isNotBlank()) { "Observation sessionId must not be blank" }
        require(sourceScanner.isNotBlank()) { "Observation sourceScanner must not be blank" }
        require(rawPayloadSha256.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            "Observation rawPayloadSha256 must be a 64-character SHA-256 hex digest"
        }
        require(parserVersion > 0) { "Observation parserVersion must be positive" }
        require(schemaVersion > 0) { "Observation schemaVersion must be positive" }
    }
}

enum class ObservationProtocol { BLUETOOTH_LE, WIFI, CELLULAR, SDR, GNSS, CLASSIC_BLUETOOTH, OTHER }
enum class ObservationIdentifierKind { BLE_ADDRESS, WIFI_BSSID, CELL_ID, RADIO_ID, NONE }
enum class BleAddressType { PUBLIC, RANDOM, ANONYMOUS, UNKNOWN }
enum class ObservationDisposition { CAPTURED, REPLAYED, LEGACY_UNVERIFIABLE }
