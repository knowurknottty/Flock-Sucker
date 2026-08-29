package com.flockyou.adversarial

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

data class BleFingerprintInput(
    val manufacturerDataLengths: Map<Int, Int>,
    val serviceUuids: List<String>,
    val serviceDataLengths: Map<String, Int>,
    val txPower: Int?,
    val nameShape: String?
)

data class BleTailObservation(
    val macAddress: String,
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val rssi: Int
)

data class BleTailAlert(
    val fingerprint: String,
    val distinctMacs: Int,
    val separatedLocations: Int,
    val maxSeparationMeters: Double,
    val journeyDurationMs: Long,
    val continuityRatio: Float,
    val confidence: Float,
    val firstSeenMs: Long,
    val lastSeenMs: Long
) {
    val proofBoundary: String = "HEURISTIC co-traveler fingerprint; not proof of a tracker or shared physical device"
}

class BleCoTravelerAnalyzer(
    private val minSeparatedLocations: Int = 3,
    private val minDistinctMacs: Int = 3,
    private val minDistanceMeters: Double = 3218.688,
    private val retentionMs: Long = 24L * 60L * 60L * 1000L,
    private val maxContinuityGapMs: Long = 45L * 60L * 1000L,
    private val minJourneyDurationMs: Long = 8L * 60L * 1000L,
    private val maxPlausibleSpeedMps: Double = 65.0,
    private val alertCooldownMs: Long = 30L * 60L * 1000L
) {
    private val history = ConcurrentHashMap<String, MutableList<BleTailObservation>>()
    private val lastAlert = ConcurrentHashMap<String, Long>()

    fun fingerprint(input: BleFingerprintInput): String {
        val normalized = buildString {
            append("m:")
            input.manufacturerDataLengths.toSortedMap().forEach { (id, len) ->
                append(id).append(':').append(len).append(',')
            }
            append("|u:").append(input.serviceUuids.map { it.lowercase() }.sorted().joinToString(","))
            append("|s:")
            input.serviceDataLengths.toSortedMap().forEach { (uuid, len) ->
                append(uuid.lowercase()).append(':').append(len).append(',')
            }
            append("|t:").append(input.txPower ?: 999)
            append("|n:").append(input.nameShape ?: "-")
        }
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            .take(10).joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun observe(fingerprint: String, observation: BleTailObservation): BleTailAlert? {
        val cutoff = observation.timestampMs - retentionMs
        val list = history.getOrPut(fingerprint) { mutableListOf() }
        list.removeAll { it.timestampMs < cutoff }
        list += observation
        if (list.size > 240) list.subList(0, list.size - 240).clear()

        val journey = strongestContinuousJourney(list) ?: return null
        val distinctMacs = journey.map { it.macAddress }.toSet().size
        if (distinctMacs < minDistinctMacs) return null

        val representatives = separatedRepresentatives(journey)
        if (representatives.size < minSeparatedLocations) return null

        val duration = journey.last().timestampMs - journey.first().timestampMs
        if (duration < minJourneyDurationMs) return null

        val maxSeparation = journey.maxOf { a -> journey.maxOf { b -> distanceMeters(a, b) } }
        val continuity = continuityRatio(journey)
        if (continuity < 0.70f) return null

        val prior = lastAlert[fingerprint] ?: 0L
        if (observation.timestampMs - prior < alertCooldownMs) return null
        lastAlert[fingerprint] = observation.timestampMs

        val confidence = (
            0.50f +
                0.05f * (representatives.size - minSeparatedLocations) +
                0.04f * (distinctMacs - minDistinctMacs) +
                0.16f * continuity
            ).coerceAtMost(0.82f)
        return BleTailAlert(
            fingerprint = fingerprint,
            distinctMacs = distinctMacs,
            separatedLocations = representatives.size,
            maxSeparationMeters = maxSeparation,
            journeyDurationMs = duration,
            continuityRatio = continuity,
            confidence = confidence,
            firstSeenMs = journey.first().timestampMs,
            lastSeenMs = journey.last().timestampMs
        )
    }

    private fun strongestContinuousJourney(source: List<BleTailObservation>): List<BleTailObservation>? {
        val sorted = source.sortedBy { it.timestampMs }
        if (sorted.isEmpty()) return null
        val sessions = mutableListOf<MutableList<BleTailObservation>>()
        var current = mutableListOf(sorted.first())
        sessions += current
        for (next in sorted.drop(1)) {
            val previous = current.last()
            val dtMs = next.timestampMs - previous.timestampMs
            val speed = if (dtMs > 0L) distanceMeters(previous, next) / (dtMs / 1000.0) else Double.POSITIVE_INFINITY
            if (dtMs <= 0L || dtMs > maxContinuityGapMs || speed > maxPlausibleSpeedMps) {
                current = mutableListOf(next)
                sessions += current
            } else {
                current += next
            }
        }
        return sessions.maxByOrNull { session ->
            session.map { it.macAddress }.toSet().size * 1000 + separatedRepresentatives(session).size * 10 + session.size
        }
    }

    private fun separatedRepresentatives(source: List<BleTailObservation>): List<BleTailObservation> {
        val representatives = mutableListOf<BleTailObservation>()
        source.sortedBy { it.timestampMs }.forEach { candidate ->
            if (representatives.isEmpty() || representatives.all { distanceMeters(it, candidate) >= minDistanceMeters }) {
                representatives += candidate
            }
        }
        return representatives
    }

    private fun continuityRatio(source: List<BleTailObservation>): Float {
        if (source.size < 2) return 0f
        val good = source.zipWithNext().count { (a, b) ->
            val dt = b.timestampMs - a.timestampMs
            if (dt <= 0L || dt > maxContinuityGapMs) return@count false
            distanceMeters(a, b) / (dt / 1000.0) <= maxPlausibleSpeedMps
        }
        return good.toFloat() / (source.size - 1).toFloat()
    }

    @Synchronized
    fun clear() {
        history.clear()
        lastAlert.clear()
    }

    private fun distanceMeters(a: BleTailObservation, b: BleTailObservation): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(b.latitude)
        val dp = Math.toRadians(b.latitude - a.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

object BleTailRegistry {
    private const val MAX_ALERTS = 50
    private val _alerts = kotlinx.coroutines.flow.MutableStateFlow<List<BleTailAlert>>(emptyList())
    val alerts: kotlinx.coroutines.flow.StateFlow<List<BleTailAlert>> = _alerts

    fun record(alert: BleTailAlert) {
        _alerts.value = (listOf(alert) + _alerts.value.filterNot { it.fingerprint == alert.fingerprint })
            .take(MAX_ALERTS)
    }
}
