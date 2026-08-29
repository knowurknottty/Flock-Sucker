package com.flockyou.adversarial

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Raw airborne Compact Position Reporting payload from TC 9..18. */
data class AirborneCprFrame(
    val icao: String,
    val odd: Boolean,
    val encodedLatitude: Int,
    val encodedLongitude: Int,
    val timestampMs: Long
)

data class AdsBPosition(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long
)

object AirborneCprDecoder {
    const val MAX_PAIR_AGE_MS = 10_000L
    private const val CPR_SCALE = 131_072.0
    private const val NZ = 15.0

    fun decodeGlobal(even: AirborneCprFrame, odd: AirborneCprFrame): AdsBPosition? {
        if (even.odd || !odd.odd || even.icao != odd.icao) return null
        if (abs(even.timestampMs - odd.timestampMs) > MAX_PAIR_AGE_MS) return null
        val yz0 = even.encodedLatitude / CPR_SCALE
        val yz1 = odd.encodedLatitude / CPR_SCALE
        val xz0 = even.encodedLongitude / CPR_SCALE
        val xz1 = odd.encodedLongitude / CPR_SCALE
        val j = floor(59.0 * yz0 - 60.0 * yz1 + 0.5).toInt()
        var lat0 = (360.0 / (4.0 * NZ)) * (mod(j, 60) + yz0)
        var lat1 = (360.0 / (4.0 * NZ - 1.0)) * (mod(j, 59) + yz1)
        if (lat0 >= 270.0) lat0 -= 360.0
        if (lat1 >= 270.0) lat1 -= 360.0
        if (lat0 !in -90.0..90.0 || lat1 !in -90.0..90.0) return null
        val nl0 = nl(lat0)
        val nl1 = nl(lat1)
        if (nl0 != nl1) return null

        val useEven = even.timestampMs >= odd.timestampMs
        val latitude = if (useEven) lat0 else lat1
        val nl = nl(latitude)
        val m = floor(xz0 * (nl - 1) - xz1 * nl + 0.5).toInt()
        val ni = max(nl - if (useEven) 0 else 1, 1)
        val xz = if (useEven) xz0 else xz1
        var longitude = (360.0 / ni) * (mod(m, ni) + xz)
        if (longitude >= 180.0) longitude -= 360.0
        if (longitude !in -180.0..180.0) return null
        return AdsBPosition(latitude, longitude, max(even.timestampMs, odd.timestampMs))
    }

    private fun mod(value: Int, divisor: Int): Int =
        value - divisor * floor(value.toDouble() / divisor.toDouble()).toInt()

    internal fun nl(latitude: Double): Int {
        val a = abs(latitude)
        if (a > 87.0) return 1
        if (a == 87.0) return 2
        if (a < 1e-12) return 59
        val lat = Math.toRadians(a)
        val denominator = cos(lat) * cos(lat)
        if (denominator <= 0.0) return 1
        val argument = 1.0 - (1.0 - cos(Math.PI / (2.0 * NZ))) / denominator
        if (argument <= -1.0 || argument >= 1.0) return 1
        return floor(2.0 * Math.PI / acos(argument)).toInt().coerceIn(1, 59)
    }
}

data class OverheadLoiterCandidate(
    val icao: String,
    val startedMs: Long,
    val lastSeenMs: Long,
    val durationMs: Long,
    val pathLengthMeters: Double,
    val medianObserverDistanceMeters: Double,
    val cumulativeTurnDegrees: Double,
    val closureRatio: Double,
    val confidence: Float,
    val proofBoundary: String = "HEURISTIC overhead loiter geometry; not proof of surveillance, ownership, agency, or intent"
)

class AdsBLoiterDetector(
    private val retentionMs: Long = 20L * 60L * 1000L,
    private val minDurationMs: Long = 3L * 60L * 1000L,
    private val maxObserverRadiusMeters: Double = 8_000.0,
    private val targetMedianRadiusMeters: Double = 5_000.0,
    private val minPathMeters: Double = 4_000.0,
    private val minCumulativeTurnDegrees: Double = 280.0,
    private val maxClosureRatio: Double = 0.55,
    private val alertCooldownMs: Long = 10L * 60L * 1000L
) {
    private data class Sample(
        val aircraft: AdsBPosition,
        val observerLatitude: Double,
        val observerLongitude: Double
    )

    private val history = mutableMapOf<String, MutableList<Sample>>()
    private val lastAlert = mutableMapOf<String, Long>()

    @Synchronized
    fun observe(
        icao: String,
        aircraft: AdsBPosition,
        observerLatitude: Double,
        observerLongitude: Double
    ): OverheadLoiterCandidate? {
        val cutoff = aircraft.timestampMs - retentionMs
        val samples = history.getOrPut(icao) { mutableListOf() }
        samples.removeAll { it.aircraft.timestampMs < cutoff }
        samples += Sample(aircraft, observerLatitude, observerLongitude)
        if (samples.size > 240) samples.subList(0, samples.size - 240).clear()
        if (samples.size < 8) return null

        val duration = samples.last().aircraft.timestampMs - samples.first().aircraft.timestampMs
        if (duration < minDurationMs) return null
        val observerDistances = samples.map {
            distanceMeters(it.aircraft.latitude, it.aircraft.longitude, it.observerLatitude, it.observerLongitude)
        }.sorted()
        val within = observerDistances.count { it <= maxObserverRadiusMeters }
        if (within.toDouble() / observerDistances.size < 0.70) return null
        val medianRadius = observerDistances[observerDistances.size / 2]
        if (medianRadius > targetMedianRadiusMeters) return null

        val legs = samples.zipWithNext()
        val pathLength = legs.sumOf { (a, b) ->
            distanceMeters(a.aircraft.latitude, a.aircraft.longitude, b.aircraft.latitude, b.aircraft.longitude)
        }
        if (pathLength < minPathMeters) return null
        val headings = legs.mapNotNull { (a, b) ->
            val d = distanceMeters(a.aircraft.latitude, a.aircraft.longitude, b.aircraft.latitude, b.aircraft.longitude)
            if (d < 75.0) null else bearingDegrees(a.aircraft.latitude, a.aircraft.longitude, b.aircraft.latitude, b.aircraft.longitude)
        }
        if (headings.size < 5) return null
        val turn = headings.zipWithNext().sumOf { (a, b) -> abs(angleDelta(a, b)) }
        if (turn < minCumulativeTurnDegrees) return null
        val first = samples.first().aircraft
        val last = samples.last().aircraft
        val displacement = distanceMeters(first.latitude, first.longitude, last.latitude, last.longitude)
        val closure = (displacement / pathLength).coerceIn(0.0, 1.0)
        if (closure > maxClosureRatio) return null
        val prior = lastAlert[icao] ?: 0L
        if (aircraft.timestampMs - prior < alertCooldownMs) return null
        lastAlert[icao] = aircraft.timestampMs

        val confidence = (
            0.52 +
                ((turn - minCumulativeTurnDegrees) / 720.0).coerceIn(0.0, 0.12) +
                ((1.0 - closure) * 0.10) +
                ((targetMedianRadiusMeters - medianRadius) / targetMedianRadiusMeters * 0.08).coerceIn(0.0, 0.08)
            ).toFloat().coerceAtMost(0.82f)
        return OverheadLoiterCandidate(
            icao = icao,
            startedMs = first.timestampMs,
            lastSeenMs = aircraft.timestampMs,
            durationMs = duration,
            pathLengthMeters = pathLength,
            medianObserverDistanceMeters = medianRadius,
            cumulativeTurnDegrees = turn,
            closureRatio = closure,
            confidence = confidence
        )
    }

    @Synchronized
    fun clear() {
        history.clear()
        lastAlert.clear()
    }

    private fun angleDelta(a: Double, b: Double): Double {
        var d = b - a
        while (d > 180.0) d -= 360.0
        while (d < -180.0) d += 360.0
        return d
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
        return 2.0 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
