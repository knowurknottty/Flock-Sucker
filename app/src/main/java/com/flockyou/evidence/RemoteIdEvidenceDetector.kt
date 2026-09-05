package com.flockyou.evidence

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class RemoteIdTransport { BLUETOOTH, WIFI_BEACON }

enum class RemoteIdMessageType(val wireType: Int) {
    BASIC_ID(0), LOCATION(1), AUTHENTICATION(2), SELF_ID(3), SYSTEM(4), OPERATOR_ID(5), MESSAGE_PACK(15), UNKNOWN(-1);

    companion object {
        fun fromWire(value: Int): RemoteIdMessageType = entries.firstOrNull { it.wireType == value } ?: UNKNOWN
    }
}

data class RemoteIdEvidence(
    val transport: RemoteIdTransport,
    val protocolVersion: Int,
    val messageTypes: Set<RemoteIdMessageType>,
    val uasIds: List<String>,
    val droneLatitude: Double?,
    val droneLongitude: Double?,
    val messageCount: Int,
    val signature: String
)

/** Strict recognizer for ASTM F3411 / OpenDroneID broadcast signatures visible to Android. */
object RemoteIdEvidenceDetector {
    private const val ASTM_REMOTE_ID_UUID = "0000fffa-0000-1000-8000-00805f9b34fb"
    private const val OPEN_DRONE_ID_APP_CODE = 0x0D
    private val WIFI_ODID_OUI = byteArrayOf(0xFA.toByte(), 0x0B, 0xBC.toByte())
    private const val WIFI_VENDOR_IE_ID = 221
    private const val MESSAGE_SIZE = 25

    fun fromBleServiceData(serviceData: Map<String, ByteArray>): RemoteIdEvidence? {
        val payload = serviceData.entries.firstOrNull { it.key.equals(ASTM_REMOTE_ID_UUID, ignoreCase = true) }?.value
            ?: return null
        if (payload.size < 3 || u8(payload[0]) != OPEN_DRONE_ID_APP_CODE) return null
        return parse(RemoteIdTransport.BLUETOOTH, payload.copyOfRange(2, payload.size), "BLE FFFA/0D")
    }

    fun fromWifiInformationElements(elements: List<WifiInformationElementEvidence>): RemoteIdEvidence? {
        for (element in elements) {
            val bytes = element.bytes
            if (element.id != WIFI_VENDOR_IE_ID || bytes.size < 6) continue
            if (!bytes.copyOfRange(0, 3).contentEquals(WIFI_ODID_OUI)) continue
            if (u8(bytes[3]) != OPEN_DRONE_ID_APP_CODE) continue
            return parse(RemoteIdTransport.WIFI_BEACON, bytes.copyOfRange(5, bytes.size), "WiFi IE221 FA:0B:BC/0D")
        }
        return null
    }

    private fun parse(transport: RemoteIdTransport, bytes: ByteArray, signature: String): RemoteIdEvidence? {
        if (bytes.isEmpty()) return null
        val outerType = u8(bytes[0]) ushr 4
        val version = u8(bytes[0]) and 0x0F
        val messages = mutableListOf<ByteArray>()
        val types = linkedSetOf<RemoteIdMessageType>()
        var advertisedCount = 1

        if (outerType == 15) {
            types += RemoteIdMessageType.MESSAGE_PACK
            if (bytes.size < 3) return null
            val singleSize = u8(bytes[1])
            advertisedCount = u8(bytes[2])
            if (singleSize != MESSAGE_SIZE || advertisedCount <= 0) return null
            var offset = 3
            repeat(advertisedCount) {
                if (offset + singleSize <= bytes.size) {
                    messages += bytes.copyOfRange(offset, offset + singleSize)
                    offset += singleSize
                }
            }
            if (messages.isEmpty()) return null
        } else {
            if (bytes.size < MESSAGE_SIZE) return null
            messages += bytes.copyOfRange(0, MESSAGE_SIZE)
        }

        val ids = linkedSetOf<String>()
        var lat: Double? = null
        var lon: Double? = null
        for (message in messages) {
            val type = RemoteIdMessageType.fromWire(u8(message[0]) ushr 4)
            types += type
            when (type) {
                RemoteIdMessageType.BASIC_ID -> decodeBasicId(message)?.let(ids::add)
                RemoteIdMessageType.LOCATION -> decodeLocation(message)?.let { (a, b) -> lat = a; lon = b }
                else -> Unit
            }
        }

        return RemoteIdEvidence(
            transport = transport,
            protocolVersion = version,
            messageTypes = types,
            uasIds = ids.toList(),
            droneLatitude = lat,
            droneLongitude = lon,
            messageCount = if (outerType == 15) advertisedCount else messages.size,
            signature = signature
        )
    }

    private fun decodeBasicId(message: ByteArray): String? {
        if (message.size < 22) return null
        val idBytes = message.copyOfRange(2, 22).takeWhile { it.toInt() != 0 }
        if (idBytes.isEmpty() || idBytes.any { u8(it) !in 0x20..0x7E }) return null
        return idBytes.toByteArray().toString(Charsets.US_ASCII).trim().takeIf { it.isNotEmpty() }
    }

    private fun decodeLocation(message: ByteArray): Pair<Double, Double>? {
        if (message.size < 13) return null
        val buffer = ByteBuffer.wrap(message).order(ByteOrder.LITTLE_ENDIAN)
        val lat = buffer.getInt(5) / 10_000_000.0
        val lon = buffer.getInt(9) / 10_000_000.0
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    private fun u8(value: Byte): Int = value.toInt() and 0xFF
}
