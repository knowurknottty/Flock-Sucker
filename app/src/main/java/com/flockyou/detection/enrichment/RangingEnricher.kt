package com.flockyou.detection.enrichment

import android.content.Context
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Ranging enrichment: precise distance measurement to surveillance-class
 * radios, on stock Android, with normal permissions.
 *
 * WiFi RTT (802.11mc FTM, API 28+): meter-level distance to APs that
 * respond to FTM. UWB (API 31+): precision ranging to UWB beacons where
 * the phone has a UWB radio.
 *
 * Truth constraints: RTT requires the target AP to support 802.11mc.
 * A non-responding AP is NOT a scanner failure — the evidence record says
 * "no FTM response" rather than inventing a distance.
 */
class RangingEnricher(private val context: Context?) {

    private val rttManager: WifiRttManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && context != null)
            context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager
        else null

    /** Whether the device supports WiFi RTT at all. */
    fun isRttSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            rttManager != null && rttManager.isAvailable

    /** Whether a specific ScanResult's AP responds to FTM. */
    fun is80211mcResponder(scanResult: android.net.wifi.ScanResult): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && scanResult.is80211mcResponder

    data class RttEvidence(
        val bssid: String,
        val distanceMeters: Int,
        val distanceStdDevMm: Int,
        val rssi: Int,
        val numAttempts: Int,
        val statusName: String
    )

    /**
     * Range a set of FTM-responder APs. Returns per-AP results with status;
     * only SUCCESS carries a distance. Non-responders reported honestly.
     */
    suspend fun rangeScanResults(
        scanResults: List<android.net.wifi.ScanResult>
    ): List<RttEvidence> {
        val manager = rttManager ?: return emptyList()
        val ctx = context ?: return emptyList()
        if (!manager.isAvailable) return emptyList()

        val responders = scanResults.filter { is80211mcResponder(it) }
        if (responders.isEmpty()) return emptyList()

        val request = RangingRequest.Builder().apply {
            addAccessPoints(responders)
        }.build()

        return suspendCancellableCoroutine { cont ->
            manager.startRanging(
                request,
                ctx.mainExecutor,
                object : RangingResultCallback() {
                    override fun onRangingResults(results: List<RangingResult>) {
                        val mapped = results.map { r ->
                            RttEvidence(
                                bssid = r.macAddress.toString(),
                                distanceMeters = if (r.status == RangingResult.STATUS_SUCCESS)
                                    r.distanceMm / 1000 else -1,
                                distanceStdDevMm = if (r.status == RangingResult.STATUS_SUCCESS)
                                    r.distanceStdDevMm else -1,
                                rssi = r.rssi,
                                numAttempts = r.numAttemptedMeasurements,
                                statusName = statusLabel(r.status)
                            )
                        }
                        cont.resume(mapped, onCancellation = null)
                    }

                    override fun onRangingFailure(code: Int) {
                        cont.resume(emptyList(), onCancellation = null)
                    }
                }
            )
        }
    }

    private fun statusLabel(status: Int): String = when (status) {
        RangingResult.STATUS_SUCCESS -> "SUCCESS"
        RangingResult.STATUS_FAIL -> "FAIL"
        RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC -> "NO_FTM_RESPONSE"
        else -> "FAIL($status)"
    }

    companion object {
        /** Evidence string for a detection record. Null when ranging gave nothing. */
        fun rttEvidenceString(evidence: List<RttEvidence>): String? {
            val successes = evidence.filter { it.statusName == "SUCCESS" && it.distanceMeters > 0 }
            if (successes.isEmpty()) return null
            return successes.joinToString("; ") { r ->
                "RTT ${r.bssid}: ${r.distanceMeters}m (±${r.distanceStdDevMm / 1000.0}m, rssi ${r.rssi}dBm)"
            }
        }

        /** UWB support probe: service presence, resolved without hard import. */
        fun isUwbSupported(context: Context?): Boolean = try {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context?.getSystemService("uwb_ranging_service") != null
        } catch (e: Exception) { false }

        /** Platform ranging capability summary. */
        fun platformRangingCapabilities(context: Context?): Map<String, Boolean> = mapOf(
            "wifi_rtt" to isRttSupportedCompat(context),
            "uwb" to isUwbSupported(context)
        )

        private fun isRttSupportedCompat(context: Context?): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && context != null
    }
}
