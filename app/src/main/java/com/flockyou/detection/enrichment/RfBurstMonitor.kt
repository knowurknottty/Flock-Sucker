package com.flockyou.detection.enrichment

/**
 * RF burst monitor: detects and classifies RF energy bursts from a stream of
 * wideband signal-strength samples. A burst is a rapid rise above threshold
 * with short duration; a burst *train* is repeating bursts with regular
 * inter-burst intervals — the signature class that covers tracking beacons,
 * surveillance transmitters, and active pulsed emitters.
 *
 * Pure-decision component: feed [process] timestamped RSSI/dB samples in
 * chronological order; read [bursts] and [trains] as first-class evidence.
 */
class RfBurstMonitor(
    private val burstThresholdDb: Int = -70,
    private val minBurstDurationMs: Long = 5,
    private val maxBurstDurationMs: Long = 2_000,
    private val trainWindowMs: Long = 30_000,
    private val minTrainsForAlert: Int = 3
) {

    data class Sample(val timestampMs: Long, val db: Int)

    data class Burst(
        val startMs: Long,
        val endMs: Long,
        val peakDb: Int
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    data class BurstTrain(
        val burstCount: Int,
        val firstStartMs: Long,
        val lastEndMs: Long,
        val medianIntervalMs: Long,
        val regularity: Float   // 0..1, 1 = metronomic
    )

    private val bursts = mutableListOf<Burst>()
    private var openBurst: Burst? = null

    val allBursts: List<Burst> get() = bursts.toList()

    /** Feed one sample; returns the burst that closed on this sample, if any. */
    fun process(sample: Sample): Burst? {
        val closed = if (sample.db >= burstThresholdDb) {
            val open = openBurst
            if (open == null) {
                openBurst = Burst(sample.timestampMs, sample.timestampMs, sample.db)
            } else {
                openBurst = open.copy(endMs = sample.timestampMs, peakDb = maxOf(open.peakDb, sample.db))
            }
            null
        } else {
            val open = openBurst
            openBurst = null
            val closed = if (open != null) closeBurst(open, sample.timestampMs) else null
            closed
        }
        return closed
    }

    /** Call at end-of-window to close a burst still above threshold. */
    fun flush(nowMs: Long): Burst? {
        val open = openBurst ?: return null
        return closeBurst(open.copy(endMs = nowMs), nowMs)
    }

    private fun closeBurst(b: Burst, closeMs: Long): Burst? {
        // Duration is onset → first below-threshold sample. A burst with a
        // single above-threshold sample still spans to its closing sample.
        val duration = closeMs - b.startMs
        val burst = b.copy(endMs = closeMs)
        openBurst = null
        if (duration < minBurstDurationMs || duration > maxBurstDurationMs) return null
        bursts.add(burst)
        return burst
    }

    /**
     * Detect burst trains: >= [minTrainsForAlert] bursts inside
     * [trainWindowMs] whose inter-burst intervals are regular
     * (median interval dominates the spread).
     */
    fun detectTrains(): List<BurstTrain> {
        if (bursts.size < minTrainsForAlert) return emptyList()
        val sorted = bursts.sortedBy { it.startMs }
        val trains = mutableListOf<BurstTrain>()
        var window = mutableListOf<Burst>()
        for (b in sorted) {
            window.add(b)
            while (window.size > 1 && b.endMs - window.first().startMs > trainWindowMs) {
                window.removeAt(0)
            }
            if (window.size >= minTrainsForAlert) {
                val intervals = window.zipWithNext().map { (a, c) -> c.startMs - a.startMs }
                val sortedIntervals = intervals.sorted()
                val median = sortedIntervals[intervals.size / 2]
                val spread = (sortedIntervals.last() - sortedIntervals.first()).toFloat()
                val regularity = if (median > 0) 1f - (spread / (median * 2)).coerceIn(0f, 1f) else 0f
                // Metronomic-ish: intervals within ±50% of median
                val regular = intervals.all { kotlin.math.abs(it - median) <= median / 2 }
                if (regular) {
                    trains.add(
                        BurstTrain(
                            burstCount = window.size,
                            firstStartMs = window.first().startMs,
                            lastEndMs = window.last().endMs,
                            medianIntervalMs = median,
                            regularity = regularity
                        )
                    )
                }
            }
        }
        // Keep the densest train (highest count, then highest regularity)
        return trains.distinctBy { listOf(it.firstStartMs, it.burstCount) }
            .sortedWith(compareByDescending<BurstTrain> { it.burstCount }.thenByDescending { it.regularity })
    }

    /** Summary evidence string for a detection record. */
    fun evidenceSummary(): String? {
        val trains = detectTrains()
        if (bursts.isEmpty()) return null
        val base = "${bursts.size} RF burst(s) detected"
        return if (trains.isNotEmpty()) {
            val t = trains.first()
            "$base; burst train: ${t.burstCount} bursts, median interval ${t.medianIntervalMs}ms, " +
                "regularity ${"%.2f".format(t.regularity)} — possible beacon/pulsed emitter"
        } else base
    }

    fun reset() {
        bursts.clear()
        openBurst = null
    }
}
