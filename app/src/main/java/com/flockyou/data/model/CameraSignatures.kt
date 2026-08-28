package com.flockyou.data.model

/**
 * Known surveillance/IP camera signatures: vendor OUI prefixes, default SSID
 * fragments, and vendor default network ports.
 *
 * Evidence basis: IEEE OUI registry entries, vendor documentation, and公开
 * vulnerability research (default-credential/RTSP exposure scans). Every entry
 * is a *candidate* indicator — an OUI or port alone is not proof of covert
 * surveillance; callers must combine signals (OUI + SSID + behavior) and
 * preserve provenance per the Flock-Sucker evidence-first contract.
 */
object CameraSignatures {

    /** One known camera-vendor signature. */
    data class CameraVendor(
        val vendor: String,
        val ouiPrefixes: List<String>,
        val defaultPorts: List<Int>,
        val commonSsidPattern: Regex? = null,
        val notes: String = ""
    )

    val vendors: List<CameraVendor> = listOf(
        CameraVendor(
            vendor = "Hikvision",
            ouiPrefixes = listOf(
                "B4:A3:82", "44:19:B6", "54:C4:15", "28:57:BE",
                "C0:56:E3", "4C:BD:8F", "18:68:CB", "C4:2F:90", "44:47:CC"
            ),
            defaultPorts = listOf(80, 443, 554, 8000, 8443, 9010),
            commonSsidPattern = Regex("(?i)^(hik|hikvision)[-_]?.*"),
            notes = "World's largest surveillance camera maker; port 8000 is the Hikvision SDK/DS protocol"
        ),
        CameraVendor(
            vendor = "Dahua",
            ouiPrefixes = listOf(
                "E0:50:8B", "3C:EF:8C", "4C:11:BF", "A0:BD:1D",
                "90:02:A9", "B0:A7:32", "3C:8C:F8"
            ),
            defaultPorts = listOf(80, 554, 37777, 37778, 34989),
            commonSsidPattern = Regex("(?i)^(dahua|dh)[-_]?.*"),
            notes = "Second largest; 37777/37778 are Dahua private TCP/UDP protocols"
        ),
        CameraVendor(
            vendor = "Axis Communications",
            ouiPrefixes = listOf("00:40:8C", "AC:CC:8E", "00:30:53", "B8:A4:4F"),
            defaultPorts = listOf(80, 443, 554, 557, 2313),
            notes = "Premium IP cameras; VAPIX API on 80/443"
        ),
        CameraVendor(
            vendor = "Bosch Security",
            ouiPrefixes = listOf("00:80:F0", "EC:A2:64"),
            defaultPorts = listOf(80, 443, 554, 8080),
            notes = "00:80:F0 shared with Panasonic historically"
        ),
        CameraVendor(
            vendor = "Hanwha Vision (Samsung Techwin/Wisenet)",
            ouiPrefixes = listOf("00:09:18", "00:16:6C", "E8:B0:69", "70:B3:D5"),
            defaultPorts = listOf(80, 554, 4520, 8080),
            notes = "Wisenet/Sunapi protocol on 4520"
        ),
        CameraVendor(
            vendor = "Amcrest / Foscam",
            ouiPrefixes = listOf("9C:8E:CD", "C0:25:67", "00:62:6E"),
            defaultPorts = listOf(80, 554, 8899, 8004, 8005),
            notes = "Consumer/DIY; 8899 is ONVIF alternate, 8004/8005 Foscam media"
        ),
        CameraVendor(
            vendor = "Reolink",
            ouiPrefixes = listOf("EC:71:DB", "B0:B5:49", "9C:8E:CD"),
            defaultPorts = listOf(80, 554, 8000, 9000),
            commonSsidPattern = Regex("(?i)^reolink[_-]?.*"),
            notes = "Common in AirBnB/hotel covert placements per field reports"
        ),
        CameraVendor(
            vendor = "Ezviz (Hikvision consumer)",
            ouiPrefixes = listOf("38:C1:CD", "D4:81:D7"),
            defaultPorts = listOf(80, 554),
            commonSsidPattern = Regex("(?i)^ezviz[_-]?.*"),
            notes = "Hikvision consumer brand"
        ),
        CameraVendor(
            vendor = "TP-Link / Tapo VIGI",
            ouiPrefixes = listOf("F8:1A:67", "50:C7:BF", "AC:84:C6"),
            defaultPorts = listOf(80, 554, 2020),
            commonSsidPattern = Regex("(?i)^(tapo|vigi|tp[_-]?link[_-]?cam).*"),
            notes = "Tapo consumer cameras; VIGI commercial line"
        ),
        CameraVendor(
            vendor = "Ubiquiti UniFi Protect",
            ouiPrefixes = listOf("44:D9:E7", "78:8A:20", "F0:9F:C2", "24:5A:4C"),
            defaultPorts = listOf(80, 443, 554, 7442, 7443, 7447),
            commonSsidPattern = Regex("(?i)^unifi[_-]?(protect|cam).*"),
            notes = "UniFi Protect RTSPS 7442/7443, RTSP 554/7447"
        ),
        CameraVendor(
            vendor = "Verkada",
            ouiPrefixes = listOf("64:16:F0", "8C:85:90"),
            defaultPorts = listOf(443, 554),
            notes = "Cloud-managed enterprise cameras; all traffic to Verkada cloud"
        ),
        CameraVendor(
            vendor = "Mobotix",
            ouiPrefixes = listOf("00:03:C5", "00:05:C6"),
            defaultPorts = listOf(80, 443, 554, 8001),
            notes = "German industrial cameras"
        ),
        CameraVendor(
            vendor = "Vivotek",
            ouiPrefixes = listOf("9C:28:B3", "00:02:D1"),
            defaultPorts = listOf(80, 554, 8001),
            notes = "Taiwanese IP camera maker"
        ),
        CameraVendor(
            vendor = "Pelco",
            ouiPrefixes = listOf("00:04:7D", "00:0B:41"),
            defaultPorts = listOf(80, 554, 3131),
            notes = "Commercial CCTV"
        ),
        CameraVendor(
            vendor = "Wyze",
            ouiPrefixes = listOf("2C:AA:8E", "D0:3F:27"),
            defaultPorts = listOf(80, 554),
            commonSsidPattern = Regex("(?i)^wyze[_-]?(cam|doorbell|setup).*"),
            notes = "Budget consumer; frequently placed without consent"
        ),
        CameraVendor(
            vendor = "Xiaomi / YI / Imilab",
            ouiPrefixes = listOf("78:8B:2A", "64:09:80", "F8:A4:5F", "28:6C:07"),
            defaultPorts = listOf(80, 554),
            commonSsidPattern = Regex("(?i)^(yi|imi|xiaomi)[_-]?cam.*"),
            notes = "YI/Imilab camera lines"
        ),
        CameraVendor(
            vendor = "Ring (Amazon)",
            ouiPrefixes = listOf("0C:80:63", "34:2C:C2", "38:EF:19", "9C:FC:01"),
            defaultPorts = listOf(80, 443),
            commonSsidPattern = Regex("(?i)^ring[_-]?(doorbell|cam|setup|stick)[_-]?[0-9a-f]*$"),
            notes = "Cloud-only; setup AP SSID is the observable signal"
        ),
        CameraVendor(
            vendor = "Arlo (Netgear)",
            ouiPrefixes = listOf("18:B4:30", "0C:47:C9", "44:73:D6"),
            defaultPorts = listOf(80, 443),
            commonSsidPattern = Regex("(?i)^arlo[_-]?(cam|pro|ultra|setup).*"),
            notes = "Base-station and camera setup APs"
        ),
        CameraVendor(
            vendor = "Eufy (Anker)",
            ouiPrefixes = listOf("24:4B:03", "AC:30:44", "C8:C9:A3"),
            defaultPorts = listOf(80, 443, 554),
            commonSsidPattern = Regex("(?i)^eufy[_-]?(cam|doorbell|security).*"),
            notes = "Local-storage consumer cameras"
        ),
        CameraVendor(
            vendor = "Blink (Amazon)",
            ouiPrefixes = listOf("58:2D:34", "44:65:0D", "74:C9:25"),
            defaultPorts = listOf(80, 443),
            commonSsidPattern = Regex("(?i)^blink[_-]?(cam|mini|setup).*"),
            notes = "Sync module setup APs"
        ),
        CameraVendor(
            vendor = "Nest / Google",
            ouiPrefixes = listOf("18:B4:30", "F4:F5:D8", "64:16:66"),
            defaultPorts = listOf(80, 443, 8443),
            commonSsidPattern = Regex("(?i)^(nest|google)[_-]?(cam|doorbell|hello).*"),
            notes = "Setup AP SSIDs observable during onboarding"
        ),
        CameraVendor(
            vendor = "Espressif (DIY/white-label cam modules)",
            ouiPrefixes = listOf("5C:CF:7F", "60:01:94", "A4:7B:9D", "24:0A:C4", "84:F3:EB"),
            defaultPorts = listOf(80, 81, 554, 8080, 8553),
            commonSsidPattern = Regex("(?i)^(esp|esp32|esp8266)[-_]?.*(cam|cam)[0-9a-f]*$"),
            notes = "ESP32-CAM class devices; 8553 is a common MicroPython/RTSP hobby port"
        ),
        CameraVendor(
            vendor = "Shenzhen white-label (TVT/Ogemray/Bilian/Reecam/iComm/B-Link)",
            ouiPrefixes = listOf(
                "00:18:AE", "7C:DD:90", "D4:D2:52", "E8:AB:FA",
                "AC:B7:4D", "EC:71:DB", "48:02:2A", "00:62:6E"
            ),
            defaultPorts = listOf(80, 554, 8004, 34567, 34599),
            notes = "OEM modules inside most cheap hidden cameras; 34567 is a common XiongMai DVR port"
        )
    )

    /** Union of all camera OUI prefixes, normalized uppercase colon format. */
    val allOuiPrefixes: Set<String> by lazy {
        vendors.flatMap { it.ouiPrefixes }.map { it.uppercase() }.toSet()
    }

    /** Union of all vendor default ports. */
    val allDefaultPorts: List<Int> by lazy {
        vendors.flatMap { it.defaultPorts }.distinct().sorted()
    }

    /** Port -> vendors that default it (for provenance in findings). */
    val portToVendors: Map<Int, List<String>> by lazy {
        val m = mutableMapOf<Int, MutableList<String>>()
        vendors.forEach { v ->
            v.defaultPorts.forEach { p -> m.getOrPut(p) { mutableListOf() }.add(v.vendor) }
        }
        m
    }

    /** Vendor SSID patterns for quick SSID matching. */
    val vendorSsidPatterns: List<Pair<Regex, String>> by lazy {
        vendors.mapNotNull { v -> v.commonSsidPattern?.let { it to v.vendor } }
    }

    /**
     * Resolve a BSSID/MAC to a camera vendor, or null. Accepts either the raw
     * MAC (uses first 3 octets) or an already-extracted "AA:BB:CC" OUI.
     */
    fun vendorForMac(macOrOui: String): CameraVendor? {
        val oui = macOrOui.uppercase().replace("-", ":").let {
            if (it.length >= 8) it.take(8) else it
        }
        return vendors.find { v -> v.ouiPrefixes.any { it.uppercase() == oui } }
    }

    /**
     * Match an SSID to a camera vendor. Returns vendor name or null.
     * Requires a non-empty SSID.
     */
    fun vendorForSsid(ssid: String): String? {
        if (ssid.isBlank()) return null
        return vendorSsidPatterns.firstOrNull { it.first.matches(ssid) }?.second
    }
}
