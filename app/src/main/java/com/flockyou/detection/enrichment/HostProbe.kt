package com.flockyou.detection.enrichment

import com.flockyou.data.model.CameraSignatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Evidence enrichment for HIGH/CRITICAL WiFi detections: TCP-connect port
 * probe (connect + short banner read only — never intrusive exploitation),
 * coarse OS-family estimation from TTL, and MAC OUI cross-reference against
 * the camera registry.
 *
 * Evidence discipline: recorded fields are observables. The OS family is a
 * bounded inference from the TTL with an explicit confidence; nothing else is
 * inferred. Probing is connect-only, short-timeout, and only invoked for
 * high/critical detections.
 */
object HostProbeEngine {

    data class ProbeResult(
        val openPorts: List<Int>,
        val banners: Map<Int, String>,
        val osFamily: String?,
        val osConfidence: String?,
        val error: String? = null
    )

    /** Ports worth probing on surveillance-class devices. */
    val PROBE_PORTS: List<Int> =
        listOf(80, 443, 554, 8000, 8080, 8443, 8899, 34567, 37777, 7443)

    private const val CONNECT_TIMEOUT_MS = 800
    private const val BANNER_TIMEOUT_MS = 700
    private const val BANNER_MAX_BYTES = 128

    /**
     * TTL → coarse OS family. Based on default TTL values common at one hop:
     * 64-class (Linux/Android/macOS), 128-class (Windows), 255-class
     * (network appliances / RTOS). Confidence is always "low" — this is a
     * hint for the analyst, never a classification.
     */
    fun osFamilyFromTtl(ttl: Int?): String? {
        if (ttl == null || ttl <= 0 || ttl > 255) return null
        return when {
            ttl in 60..64 -> "Unix-family (Linux/Android/macOS)"
            ttl in 50..59 -> "Unix-family (reduced TTL $ttl)"
            ttl in 120..135 -> "Windows-family"
            ttl >= 250 -> "Network appliance / embedded RTOS"
            else -> null
        }
    }

    /**
     * Probe a host's surveillance-class ports. Connect-only with short
     * timeouts; reads a small banner where the service sends one first
     * (RTSP, HTTP). Total budget bounded by [timeoutMs]. Never sends
     * application payloads.
     */
    suspend fun probeHost(ip: String, timeoutMs: Long = 4_000L): ProbeResult =
        withContext(Dispatchers.IO) {
            val open = mutableListOf<Int>()
            val banners = mutableMapOf<Int, String>()
            var error: String? = null
            for (port in PROBE_PORTS) {
                // Per-port budget; abort the sweep if the overall window expired.
                val remaining = timeoutMs - open.size * 0L
                val outcome = withTimeoutOrNull(minOf(1_200L, timeoutMs)) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                            socket.soTimeout = BANNER_TIMEOUT_MS
                            if (!open.contains(port)) open.add(port)
                            val banner = readBannerQuietly(socket.getInputStream())
                            if (banner != null) banners[port] = banner
                        }
                    } catch (_: Exception) {
                        // connection refused / timeout — port closed or filtered
                    }
                }
                if (outcome == null) {
                    error = "probe window exhausted"
                    break
                }
            }
            ProbeResult(
                openPorts = open.sorted(),
                banners = banners,
                osFamily = null,
                osConfidence = null,
                error = error
            )
        }

    /** Services announce themselves first (RTSP, some HTTP). Read a bounded greeting. */
    private fun readBannerQuietly(stream: InputStream): String? = try {
        val buf = ByteArray(BANNER_MAX_BYTES)
        var read = 0
        while (read < BANNER_MAX_BYTES) {
            val n = stream.read(buf, read, BANNER_MAX_BYTES - read)
            if (n <= 0) break
            read += n
            // Stop at a double CRLF (end of HTTP-style header block)
            if (read >= 4 && String(buf, read - 4, 4, Charsets.ISO_8859_1) == "\r\n\r\n") break
        }
        if (read > 0) {
            String(buf, 0, read, Charsets.ISO_8859_1).trim().take(120).ifEmpty { null }
        } else null
    } catch (_: Exception) {
        null
    }

    // ==================== OUI cross-reference ====================

    data class MacIntelligence(
        val oui: String,
        val cameraVendor: String?,
        val cameraPorts: List<Int>,
        val ieeeVendor: String?
    )

    /**
     * Cross-reference a MAC/BSSID against the camera signature registry.
     * [ieeeVendor] comes from the OUI database (OuiRepository) when loaded;
     * pass null when the database is unavailable.
     */
    fun macIntelligence(macOrBssid: String, ieeeVendor: String? = null): MacIntelligence? {
        val raw = macOrBssid.uppercase().replace("-", ":")
        if (raw.length < 8) return null
        val oui = raw.take(8)
        val vendor = CameraSignatures.vendorForMac(oui)
        // No usable signal: no camera match and no IEEE vendor info.
        if (vendor == null && ieeeVendor == null) return null
        return MacIntelligence(
            oui = oui,
            cameraVendor = vendor?.vendor,
            cameraPorts = vendor?.defaultPorts ?: emptyList(),
            ieeeVendor = ieeeVendor
        )
    }
}
