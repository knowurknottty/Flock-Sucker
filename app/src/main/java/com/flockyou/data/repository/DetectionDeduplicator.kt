package com.flockyou.data.repository

import android.util.Log
import com.flockyou.data.model.Detection
import com.flockyou.evidence.IdentityDecisionClass
import com.flockyou.evidence.IdentityResolver
import com.flockyou.data.model.DeviceType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detection Deduplicator
 *
 * Provides conservative deduplication for the compatibility Detection layer.
 * Exact observed addresses may be throttled briefly, while canonical identity
 * matching is delegated to IdentityResolver. Weak metadata never merges devices.
 */
@Singleton
class DetectionDeduplicator @Inject constructor() {
    private val identityResolver = IdentityResolver()

    companion object {
        private const val TAG = "DetectionDeduplicator"

        // Throttling configuration
        private const val DEFAULT_THROTTLE_WINDOW_MS = 5_000L  // 5 seconds
        private const val BLE_THROTTLE_WINDOW_MS = 3_000L     // 3 seconds for BLE (faster scanning)
        private const val WIFI_THROTTLE_WINDOW_MS = 10_000L   // 10 seconds for WiFi

        // Cleanup settings
        private const val THROTTLE_CACHE_CLEANUP_INTERVAL_MS = 60_000L  // 1 minute
        private const val MAX_THROTTLE_CACHE_SIZE = 1000
    }

    // Track last detection time for each device key (MAC, SSID, or composite)
    private val lastDetectionTimes = ConcurrentHashMap<String, Long>()
    private var lastCleanupTime = System.currentTimeMillis()

    /**
     * Check if a detection should be throttled (rapid detection suppression).
     *
     * Returns true if the same device was detected very recently and should
     * be suppressed to avoid flooding the database with duplicate entries.
     *
     * @param detection The detection to check
     * @return true if the detection should be throttled, false otherwise
     */
    fun shouldThrottle(detection: Detection): Boolean {
        val now = System.currentTimeMillis()

        // Periodic cleanup
        if (now - lastCleanupTime > THROTTLE_CACHE_CLEANUP_INTERVAL_MS) {
            cleanupThrottleCache()
            lastCleanupTime = now
        }

        // Generate throttle key from available identifiers
        val throttleKey = generateThrottleKey(detection)

        // Get appropriate throttle window based on protocol
        val throttleWindow = getThrottleWindow(detection)

        val lastSeen = lastDetectionTimes[throttleKey]
        if (lastSeen != null && (now - lastSeen) < throttleWindow) {
            return true  // Throttle - too recent
        }

        // Update last seen time
        lastDetectionTimes[throttleKey] = now

        // Enforce cache size limit
        if (lastDetectionTimes.size > MAX_THROTTLE_CACHE_SIZE) {
            evictOldestEntries()
        }

        return false  // Don't throttle
    }

    /**
     * Find a canonical identity match from candidate detections.
     * Weak similarity decisions are intentionally not returned as matches.
     *
     * @param detection The new detection to match
     * @param candidates List of potential matching detections from the database
     * @return The matching detection if found, null otherwise
     */
    fun findMatch(detection: Detection, candidates: List<Detection>): Detection? {
        if (candidates.isEmpty()) return null

        return candidates.firstOrNull { candidate ->
            val decision = identityResolver.resolve(detection, candidate)
            if (decision.decision == IdentityDecisionClass.MATCH) {
                Log.d(
                    TAG,
                    "Identity match: rule=${decision.ruleId}, score=${decision.score}, deviceType=${detection.deviceType}"
                )
                true
            } else {
                false
            }
        }
    }

    /**
     * Generate a throttle key from detection identifiers.
     * Uses the most specific identifier available.
     */
    private fun generateThrottleKey(detection: Detection): String {
        // Throttling is allowed only on an exact observed radio address. SSID,
        // service UUID, name, manufacturer, type, and advertisement shape are
        // similarity evidence and must never suppress a distinct device.
        val address = detection.macAddress?.trim()?.uppercase()
        return if (!address.isNullOrBlank()) {
            "address:${detection.protocol}:$address"
        } else {
            "event:${detection.id}"
        }
    }

    /**
     * Get the appropriate throttle window based on detection protocol.
     */
    private fun getThrottleWindow(detection: Detection): Long {
        return when (detection.protocol) {
            com.flockyou.data.model.DetectionProtocol.BLUETOOTH_LE -> BLE_THROTTLE_WINDOW_MS
            com.flockyou.data.model.DetectionProtocol.WIFI -> WIFI_THROTTLE_WINDOW_MS
            else -> DEFAULT_THROTTLE_WINDOW_MS
        }
    }

    /**
     * Clean up old entries from the throttle cache.
     */
    private fun cleanupThrottleCache() {
        val cutoff = System.currentTimeMillis() - WIFI_THROTTLE_WINDOW_MS * 2  // Keep 2x longest window
        val iterator = lastDetectionTimes.entries.iterator()
        var removed = 0

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff) {
                iterator.remove()
                removed++
            }
        }

        if (removed > 0) {
            Log.d(TAG, "Cleaned up $removed stale throttle entries, ${lastDetectionTimes.size} remaining")
        }
    }

    /**
     * Evict oldest entries when cache is full.
     */
    private fun evictOldestEntries() {
        val entriesToRemove = lastDetectionTimes.entries
            .sortedBy { it.value }
            .take(MAX_THROTTLE_CACHE_SIZE / 4)
            .map { it.key }

        entriesToRemove.forEach { lastDetectionTimes.remove(it) }
        Log.d(TAG, "Evicted ${entriesToRemove.size} oldest throttle entries")
    }

    /**
     * Clear all throttle state. Call when starting a new scan session.
     */
    fun clearThrottleState() {
        lastDetectionTimes.clear()
        Log.d(TAG, "Throttle state cleared")
    }

    /**
     * Get current throttle cache size (for diagnostics).
     */
    fun getThrottleCacheSize(): Int = lastDetectionTimes.size
}
