package com.flockyou.evidence

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult as BleScanResult
import android.location.Location
import android.net.wifi.ScanResult as WifiScanResult
import android.os.Build
import android.os.SystemClock
import com.flockyou.data.model.BleAddressType
import com.flockyou.data.model.Observation
import java.util.UUID

object AndroidObservationAdapter {
    fun fromBle(
        result: BleScanResult,
        sessionId: String,
        location: Location?,
        scannerHealthGeneration: Long = 0
    ): Observation {
        val record = result.scanRecord
        val manufacturerData = buildMap<Int, ByteArray> {
            record?.manufacturerSpecificData?.let { sparse ->
                for (index in 0 until sparse.size()) {
                    put(sparse.keyAt(index), sparse.valueAt(index).copyOf())
                }
            }
        }
        val serviceData = record?.serviceData.orEmpty()
            .mapKeys { it.key.uuid.toString() }
            .mapValues { it.value.copyOf() }
        return ObservationFactory.fromBle(
            BleObservationInput(
                observationId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                timestamp = wallClockFromElapsedNanos(result.timestampNanos),
                elapsedRealtimeNanos = result.timestampNanos,
                sourceScanner = "ANDROID_BLE",
                scannerHealthGeneration = scannerHealthGeneration,
                observedAddress = result.device.address,
                addressType = readAddressType(result.device),
                deviceName = result.device.name,
                rssi = result.rssi,
                txPower = result.txPower.takeUnless { it == Int.MIN_VALUE },
                advertisedTxPower = record?.txPowerLevel?.takeUnless { it == Int.MIN_VALUE },
                primaryPhy = result.primaryPhy,
                secondaryPhy = result.secondaryPhy,
                advertisingSid = result.advertisingSid.takeUnless { it < 0 },
                periodicAdvertisingInterval = result.periodicAdvertisingInterval.takeUnless { it == 0 },
                manufacturerData = manufacturerData,
                serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
                serviceData = serviceData,
                rawScanRecord = record?.bytes?.copyOf(),
                dataStatus = result.dataStatus,
                connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else null,
                legacy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isLegacy else null,                latitude = location?.latitude,
                longitude = location?.longitude,
                altitudeMeters = location?.altitude?.takeIf { location.hasAltitude() },
                accuracyMeters = location?.accuracy?.takeIf { location.hasAccuracy() }
            )
        )
    }

    fun fromWifi(
        result: WifiScanResult,
        sessionId: String,
        location: Location?,
        scannerHealthGeneration: Long = 0
    ): Observation {
        val elements = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                result.informationElements.orEmpty().mapNotNull { ie ->
                    val buffer = ie.bytes ?: return@mapNotNull null
                    val duplicate = buffer.duplicate()
                    val bytes = ByteArray(duplicate.remaining())
                    duplicate.get(bytes)
                    WifiInformationElementEvidence(ie.id, ie.idExt, bytes)
                }
            }.getOrDefault(emptyList())
        } else emptyList()
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.wifiSsid?.toString()?.removeSurrounding("\"")
        } else {
            @Suppress("DEPRECATION")
            result.SSID
        }

        return ObservationFactory.fromWifi(
            WifiObservationInput(
                observationId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                timestamp = wallClockFromWifiMicros(result.timestamp),
                elapsedRealtimeNanos = result.timestamp * 1_000L,
                sourceScanner = "ANDROID_WIFI_MANAGER",
                scannerHealthGeneration = scannerHealthGeneration,
                bssid = result.BSSID,
                ssid = ssid,
                rssi = result.level,
                frequencyMhz = result.frequency,
                channelWidth = result.channelWidth,
                centerFreq0 = result.centerFreq0,
                centerFreq1 = result.centerFreq1,
                capabilities = result.capabilities,
                informationElements = elements,
                latitude = location?.latitude,
                longitude = location?.longitude,
                altitudeMeters = location?.altitude?.takeIf { location.hasAltitude() },
                accuracyMeters = location?.accuracy?.takeIf { location.hasAccuracy() }
            )
        )
    }
    private fun wallClockFromElapsedNanos(observedElapsedNanos: Long): Long {
        val deltaNanos = SystemClock.elapsedRealtimeNanos() - observedElapsedNanos
        return System.currentTimeMillis() - (deltaNanos / 1_000_000L)
    }

    private fun wallClockFromWifiMicros(observedElapsedMicros: Long): Long =
        wallClockFromElapsedNanos(observedElapsedMicros * 1_000L)

    private fun readAddressType(device: BluetoothDevice): BleAddressType {
        val value = runCatching {
            BluetoothDevice::class.java.getMethod("getAddressType").invoke(device) as Int
        }.getOrNull() ?: return BleAddressType.UNKNOWN

        return when (value) {
            0 -> BleAddressType.PUBLIC
            1 -> BleAddressType.RANDOM
            0xFF -> BleAddressType.ANONYMOUS
            else -> BleAddressType.UNKNOWN
        }
    }
}
