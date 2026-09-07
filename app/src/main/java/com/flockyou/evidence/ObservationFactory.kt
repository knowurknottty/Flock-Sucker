package com.flockyou.evidence

import com.flockyou.data.model.BleAddressType
import com.flockyou.data.model.Observation
import com.flockyou.data.model.ObservationIdentifierKind
import com.flockyou.data.model.ObservationProtocol
import com.google.gson.Gson
import java.security.MessageDigest

/** Pure, deterministic normalization from scanner evidence into immutable observations. */
object ObservationFactory {
    private val gson = Gson()

    fun fromBle(input: BleObservationInput): Observation {
        val manufacturers = input.manufacturerData.toSortedMap()
            .mapValues { (_, bytes) -> bytes.toHex() }
        val uuids = input.serviceUuids.map { it.uppercase() }.sorted()
        val serviceData = input.serviceData.toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .mapKeys { it.key.uppercase() }
            .mapValues { (_, bytes) -> bytes.toHex() }
        val fallbackPayload = gson.toJson(
            linkedMapOf(
                "address" to input.observedAddress.uppercase(),
                "manufacturerData" to manufacturers,
                "serviceUuids" to uuids,
                "serviceData" to serviceData
            )
        ).toByteArray()
        val payload = input.rawScanRecord ?: fallbackPayload

        return Observation(
            id = input.observationId,
            sessionId = input.sessionId,
            timestamp = input.timestamp,
            elapsedRealtimeNanos = input.elapsedRealtimeNanos,
            protocol = ObservationProtocol.BLUETOOTH_LE,
            sourceScanner = input.sourceScanner,
            scannerHealthGeneration = input.scannerHealthGeneration,
            observedIdentifier = input.observedAddress.uppercase(),
            identifierKind = ObservationIdentifierKind.BLE_ADDRESS,            bleAddressType = input.addressType,
            deviceName = input.deviceName,
            rssi = input.rssi,
            txPower = input.txPower,
            primaryPhy = input.primaryPhy,
            secondaryPhy = input.secondaryPhy,
            advertisingSid = input.advertisingSid,
            periodicAdvertisingInterval = input.periodicAdvertisingInterval,
            manufacturerDataJson = gson.toJson(manufacturers),
            serviceUuidsJson = gson.toJson(uuids),
            serviceDataJson = gson.toJson(serviceData),
            rawPayloadSha256 = payload.sha256(),
            rawMetadata = gson.toJson(
                linkedMapOf(
                    "rawScanRecordHex" to input.rawScanRecord?.toHex(),
                    "advertisedTxPower" to input.advertisedTxPower,
                    "dataStatus" to input.dataStatus,
                    "connectable" to input.connectable,
                    "legacy" to input.legacy
                )
            ),
            latitude = input.latitude,
            longitude = input.longitude,
            altitudeMeters = input.altitudeMeters,
            accuracyMeters = input.accuracyMeters,
            parserVersion = CURRENT_PARSER_VERSION,
            schemaVersion = CURRENT_SCHEMA_VERSION
        )
    }

    fun fromWifi(input: WifiObservationInput): Observation {
        val elements = input.informationElements.map { element ->
            linkedMapOf("id" to element.id, "idExt" to element.idExt, "bytes" to element.bytes.toHex())
        }
        val canonicalPayload = gson.toJson(
            linkedMapOf(
                "bssid" to input.bssid?.uppercase(),
                "ssid" to input.ssid,
                "frequencyMhz" to input.frequencyMhz,
                "channelWidth" to input.channelWidth,
                "informationElements" to elements
            )
        ).toByteArray()
        return Observation(
            id = input.observationId,
            sessionId = input.sessionId,
            timestamp = input.timestamp,
            elapsedRealtimeNanos = input.elapsedRealtimeNanos,
            protocol = ObservationProtocol.WIFI,
            sourceScanner = input.sourceScanner,
            scannerHealthGeneration = input.scannerHealthGeneration,
            observedIdentifier = input.bssid?.uppercase(),
            identifierKind = if (input.bssid == null) ObservationIdentifierKind.NONE else ObservationIdentifierKind.WIFI_BSSID,
            ssid = input.ssid,
            rssi = input.rssi,
            frequencyMhz = input.frequencyMhz,
            channelWidth = input.channelWidth,
            informationElementsJson = gson.toJson(elements),
            rawPayloadSha256 = canonicalPayload.sha256(),
            rawMetadata = gson.toJson(
                linkedMapOf(
                    "capabilities" to input.capabilities,
                    "centerFreq0" to input.centerFreq0,
                    "centerFreq1" to input.centerFreq1,
                    "wifiStandard" to input.wifiStandard,
                    "securityTypes" to input.securityTypes
                )
            ),
            latitude = input.latitude,
            longitude = input.longitude,
            altitudeMeters = input.altitudeMeters,
            accuracyMeters = input.accuracyMeters,
            parserVersion = CURRENT_PARSER_VERSION,
            schemaVersion = CURRENT_SCHEMA_VERSION
        )
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    private const val CURRENT_PARSER_VERSION = 1
    private const val CURRENT_SCHEMA_VERSION = 1
}

data class BleObservationInput(
    val observationId: String,
    val sessionId: String,
    val timestamp: Long,
    val elapsedRealtimeNanos: Long? = null,
    val sourceScanner: String,
    val scannerHealthGeneration: Long = 0,
    val observedAddress: String,
    val addressType: BleAddressType = BleAddressType.UNKNOWN,
    val deviceName: String? = null,
    val rssi: Int? = null,
    val txPower: Int? = null,
    val advertisedTxPower: Int? = null,
    val primaryPhy: Int? = null,
    val secondaryPhy: Int? = null,
    val advertisingSid: Int? = null,
    val periodicAdvertisingInterval: Int? = null,
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids: List<String> = emptyList(),
    val serviceData: Map<String, ByteArray> = emptyMap(),
    val rawScanRecord: ByteArray? = null,
    val dataStatus: Int? = null,
    val connectable: Boolean? = null,
    val legacy: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null
)
data class WifiObservationInput(
    val observationId: String,
    val sessionId: String,
    val timestamp: Long,
    val elapsedRealtimeNanos: Long? = null,
    val sourceScanner: String,
    val scannerHealthGeneration: Long = 0,
    val bssid: String?,
    val ssid: String? = null,
    val rssi: Int? = null,
    val frequencyMhz: Int? = null,
    val channelWidth: Int? = null,
    val centerFreq0: Int? = null,
    val centerFreq1: Int? = null,
    val wifiStandard: Int? = null,
    val securityTypes: List<Int> = emptyList(),
    val capabilities: String? = null,
    val informationElements: List<WifiInformationElementEvidence> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null
)

data class WifiInformationElementEvidence(
    val id: Int,
    val idExt: Int,
    val bytes: ByteArray
)
