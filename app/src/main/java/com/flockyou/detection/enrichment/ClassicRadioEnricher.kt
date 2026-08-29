package com.flockyou.detection.enrichment

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Bluetooth Classic discovery + WiFi Information-Element fingerprinting.
 *
 * BLE-only scanning misses surveillance gear on Bluetooth Classic (SPP
 * audio bugs, older trackers, classic-only OEM modules). `startDiscovery()`
 * sees classic devices on stock Android with normal permissions.
 *
 * WiFi Information Elements (API 30+): `ScanResult.InformationElement` ex-
 * poses vendor-specific IEs. Covert APs frequently leak chipset identity in
 * vendor IEs (Realtek, Hi3516-class SoC strings). We capture and match.
 */
object ClassicRadioEnricher {

    // ==================== Bluetooth Classic ====================

    data class ClassicDevice(
        val macAddress: String,
        val name: String?,
        val deviceClass: Int,
        val rssi: Short?,
        val timestampMs: Long
    ) {
        /** Bluetooth Class of Device major service classes. */
        val serviceClasses: List<String>
            get() = buildList {
                if (deviceClass and 0x10000 != 0) add("POSITIONING")
                if (deviceClass and 0x20000 != 0) add("NETWORKING")
                if (deviceClass and 0x40000 != 0) add("RENDERING")
                if (deviceClass and 0x80000 != 0) add("CAPTURE")
                if (deviceClass and 0x100000 != 0) add("OBJECT_TRANSFER")
                if (deviceClass and 0x200000 != 0) add("AUDIO")
                if (deviceClass and 0x400000 != 0) add("TELEPHONY")
                if (deviceClass and 0x800000 != 0) add("INFORMATION")
            }

        /** Major device class (bits 8-12). */
        val majorDeviceClass: String
            get() = when ((deviceClass and 0x1F00) shr 8) {
                0x00 -> "MISC"
                0x01 -> "COMPUTER"
                0x02 -> "PHONE"
                0x03 -> "LAN_ACCESS"
                0x04 -> "AUDIO_VIDEO"
                0x05 -> "PERIPHERAL"
                0x06 -> "IMAGING"
                0x07 -> "WEARABLE"
                0x08 -> "TOY"
                0x09 -> "HEALTH"
                else -> "UNCATEGORIZED"
            }

        /** Surveillance-relevant flags: capture+audio is a bug candidate. */
        val surveillanceRelevant: Boolean
            get() {
                val capture = deviceClass and 0x80000 != 0
                val audio = deviceClass and 0x200000 != 0
                return capture && (audio || deviceClass and 0x40000 != 0)
            }
    }

    /** Bluetooth Classic OUI prefixes on covert audio bugs (compact hex). */
    val SUSPECT_CLASSIC_OUIS_COMPACT: Set<String> = setOf(
        "001A7D", "000272", "001583"
    )

    /** Register a discovery receiver; returns the results collector. */
    fun registerDiscovery(
        context: Context,
        adapter: BluetoothAdapter?,
        onDevice: (ClassicDevice) -> Unit
    ): BroadcastReceiver? {
        val a = adapter ?: return null
        if (!a.isEnabled) return null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != android.bluetooth.BluetoothDevice.ACTION_FOUND) return
                val device = intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(
                    android.bluetooth.BluetoothDevice.EXTRA_DEVICE) ?: return
                val rssi = intent.getShortExtra(
                    android.bluetooth.BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                onDevice(
                    ClassicDevice(
                        macAddress = device.address ?: return,
                        name = device.name,
                        deviceClass = device.bluetoothClass?.deviceClass ?: 0,
                        rssi = if (rssi != Short.MIN_VALUE) rssi else null,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }
        }
        context.registerReceiver(receiver,
            IntentFilter(android.bluetooth.BluetoothDevice.ACTION_FOUND))
        if (a.startDiscovery()) return receiver
        runCatching { context.unregisterReceiver(receiver) }
        return null
    }

    /** BluetoothDevice.EXTRA_RSSI constant (pre-33 availability workaround). */
    private const val BluetoothDevice_EXTRA_RSSI_COMPAT = "android.bluetooth.device.extra.RSSI"

    /** Match a classic device against the covert-bug OUI list (compact hex). */
    fun isSuspectOui(mac: String): Boolean {
        val compact = mac.uppercase().replace(":", "").replace("-", "")
        if (compact.length < 6) return false
        return SUSPECT_CLASSIC_OUIS_COMPACT.contains(compact.take(6))
    }

    // ==================== WiFi Information Elements ====================

    /** Vendor IE chipset fingerprints (API 30+ ScanResult.InformationElement). */
    val VENDOR_IE_FINGERPRINTS = listOf(
        "realtek" to "Realtek covert-AP chipset",
        "hi3516" to "HiSilicon Hi3516 camera SoC",
        "ingenic" to "Ingenic camera SoC",
        "anyka" to "Anyka camera SoC",
        "goke" to "Goke camera SoC",
        "fullhan" to "Fullhan camera SoC"
    )

    /**
     * Extract chipset fingerprints from WiFi scan result IEs.
     * Requires API 30+ and the ScanResult's informationElements field.
     * Returns empty when none matched (honest: most APs leak nothing).
     */
    fun ieFingerprints(scanResult: android.net.wifi.ScanResult): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val elements = try {
            scanResult.informationElements ?: return emptyList()
        } catch (e: Exception) { return emptyList() }
        val hits = mutableListOf<String>()
        for (ie in elements) {
            val bb = ie.bytes ?: continue
            val bytes = ByteArray(bb.remaining())
            bb.get(bytes)
            val text = String(bytes, Charsets.ISO_8859_1).lowercase()
            for ((needle, label) in VENDOR_IE_FINGERPRINTS) {
                if (text.contains(needle)) hits.add(label)
            }
        }
        return hits.distinct()
    }

    // ==================== DHCP fingerprinting class ====================

    /**
     * DHCP option-55 / vendor-class fingerprints of common camera SoCs.
     * Fed from the network stack when the device joins a network and
     * observes neighbor DHCP traffic (e.g., via a VPNService pass-through)
     * — or recorded from on-device DHCP exchanges where the phone is the
     * client. This is the option-55 signature table, no capture logic.
     */
    val DHCP_FINGERPRINTS: Map<String, List<Int>> = mapOf(
        // Option 55 parameter request list signatures
        "hikvision-dh" to listOf(1, 3, 6, 15, 28, 51, 54, 58, 59),
        "dahua-dh" to listOf(1, 3, 6, 28, 51, 58, 59),
        "ingenic-cam" to listOf(1, 3, 6, 15, 28, 51),
        "android-phone" to listOf(1, 3, 6, 15, 28, 51, 58, 59)
    )

    /** Match a captured option-55 list against known camera SoC signatures. */
    fun matchDhcpFingerprint(option55: List<Int>): String? =
        DHCP_FINGERPRINTS.entries.firstOrNull { (_, sig) ->
            sig.sorted() == option55.sorted()
        }?.key
}
