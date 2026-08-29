package com.flockyou.detection.silentsms

/**
 * Hybrid Silent-SMS (Type-0 / SMS control-plane) surveillance detection.
 *
 * Two evidence paths, explicitly labeled:
 *
 * EXACT — a privileged sensor directly observes the protocol event:
 *   - Shannon modem diag (existing ShannonDiagMonitor + NasMessageParser
 *     SilentSms signaling event) on supported Samsung hardware
 *   - any future modem-diagnostic channel exposed by the platform
 *   This module defines the generic exact-event abstraction; the Shannon
 *   parser remains the concrete implementation where available.
 *
 * INDIRECT — stock Android public APIs cannot expose Type-0 SMS records.
 * Per the truth constraints, that fact is stated plainly: on an unmodified
 * device the exact event is UNAVAILABLE. What IS observable are correlated
 * side-effects on the telephony plane, and this module correlates them:
 *   - service-state churn (in-service ↔ emergency-only ↔ out-of-service flips)
 *   - registration / data-registration transitions without user action
 *   - RAT changes and downgrades (LTE→GSM redirects pair with silent-SMS
 *     tradecraft)
 *   - cell reselection / identity churn (sudden new-cell discovery while
 *     stationary)
 *   - signal discontinuities (sharp RSSI swings unexplained by movement)
 *   - subscription/SIM state changes where observable
 *
 * Correlation produces an ANOMALY SCORE with explicit confidence and the
 * proof boundary printed into the evidence. A high indirect score is a
 * reason to look, never a claim that a silent SMS occurred.
 */

/** Source class for a detection record. */
enum class SourceClass(val displayName: String, val proofBoundary: String) {
    /**
     * Radio-log evidence: the radio log buffer (READ_LOGS grant) directly
     * named the event (Type-0 marker, TP-PID 0x40). The log line IS the
     * evidence and is captured verbatim — but log fidelity varies by OEM
     * and driver, so this is exact-at-the-log, not a modem-diag capture.
     */
    EXACT_BY_LOG("EXACT (radio log)", "Radio log directly named the silent-SMS event; verbatim log line captured; log fidelity varies by OEM"),
    EXACT("EXACT", "Privileged sensor directly observed the silent-SMS protocol event"),
    INDIRECT("INDIRECT", "Anomaly inferred from correlated telephony side-effects; no direct proof of a silent SMS; benign causes remain possible")
}

/** A single correlated telephony observation. */
data class TelephonyObservation(
    val timestampMs: Long,
    val kind: Kind,
    /** Free-form detail: from-state, to-state, cell id, RAT name, etc. */
    val detail: String,
    /** Optional metric (RSSI delta dB, service-state change count, ...). */
    val magnitude: Float? = null
) {
    enum class Kind {
        SERVICE_STATE_CHURN,
        REGISTRATION_TRANSITION,
        DATA_REGISTRATION_TRANSITION,
        RAT_CHANGE,
        RAT_DOWNGRADE,
        CELL_RESELECTION,
        NEW_CELL_DISCOVERY,
        SIGNAL_DISCONTINUITY,
        SIM_STATE_CHANGE
    }
}

/** Generic exact-event abstraction: any privileged sensor that directly observes a silent-SMS event implements this. */
interface ExactSilentSmsSensor {
    /** Whether this sensor is currently available on this device. */
    fun isAvailable(): Boolean

    /**
     * The most recent directly-observed silent-SMS event, or null. The
     * implementation owns its buffer; this is a read of verified evidence.
     */
    fun pollLatest(): ExactSilentSmsEvent?
}

/** Directly-observed silent-SMS event (EXACT path only). */
data class ExactSilentSmsEvent(
    val timestampMs: Long,
    /** Protocol origin, e.g. "shannon-diag/NAS-CP-DATA Type-0 SMS-PP" */
    val sensorPath: String,
    /** Raw protocol evidence (frame bytes summary, TP-PID/TP-DCS where parsed). */
    val protocolDetail: String
)

/** Result record for a detection evaluation. */
data class SilentSmsAssessment(
    val timestampMs: Long,
    val sourceClass: SourceClass,
    val confidence: Float,          // 0..1
    val sensorPath: String,         // which sensor/observer produced this
    val proofBoundary: String,      // explicit statement of what this proves and does not
    val observations: List<TelephonyObservation>,  // INDIRECT path
    val exactEvent: ExactSilentSmsEvent?,           // EXACT path
    /** Human-readable anomaly summary (INDIRECT path only). */
    val anomalySummary: String?
)

/**
 * Correlation engine. Feed telephony observations as they occur; ask for an
 * assessment on demand. Scoring is additive with explicit weights and a
 * bounded time window; it is deliberately transparent so the proof boundary
 * is auditable.
 */
class IndirectSilentSmsCorrelator(
    private val windowMs: Long = 120_000L,      // 2-minute correlation window
    private val currentSimState: () -> String? = { null }
) {

    private val observations = ArrayDeque<TelephonyObservation>()

    // Explicit, auditable weights (scaled 0..1 each)
    internal fun weight(kind: TelephonyObservation.Kind): Float = when (kind) {
        TelephonyObservation.Kind.SERVICE_STATE_CHURN -> 0.25f
        TelephonyObservation.Kind.REGISTRATION_TRANSITION -> 0.20f
        TelephonyObservation.Kind.DATA_REGISTRATION_TRANSITION -> 0.15f
        TelephonyObservation.Kind.RAT_CHANGE -> 0.15f
        TelephonyObservation.Kind.RAT_DOWNGRADE -> 0.30f
        TelephonyObservation.Kind.CELL_RESELECTION -> 0.15f
        TelephonyObservation.Kind.NEW_CELL_DISCOVERY -> 0.25f
        TelephonyObservation.Kind.SIGNAL_DISCONTINUITY -> 0.20f
        TelephonyObservation.Kind.SIM_STATE_CHANGE -> 0.10f
    }

    fun observe(observation: TelephonyObservation) {
        observations.addLast(observation)
        // Prune relative to the newest observation time (not wall clock) so
        // the correlator is testable with synthetic timestamps and robust
        // to clock skew.
        prune(observation.timestampMs)
    }

    private fun prune(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (observations.isNotEmpty() && observations.first().timestampMs < cutoff) {
            observations.removeFirst()
        }
    }

    /**
     * Evaluate the current window. Returns null when there is nothing to
     * report (score below reporting threshold).
     */
    fun assess(nowMs: Long? = null): SilentSmsAssessment? {
        // Default evaluation time = newest observation (robust to synthetic
        // timestamps and clock skew); explicit nowMs overrides for testing.
        val effectiveNow = nowMs ?: observations.lastOrNull()?.timestampMs ?: return null
        prune(effectiveNow)
        if (observations.isEmpty()) return null
        if (observations.isEmpty()) return null

        // Distinct-kind coverage matters more than raw count: many identical
        // service-state flips is congestion; several different anomaly classes
        // inside one window is the correlation signature.
        val byKind = observations.groupBy { it.kind }
        var score = 0f
        val notes = mutableListOf<String>()
        for ((kind, list) in byKind) {
            // First hit full weight; repeats taper geometrically:
            // multiplier = 2 - 0.5^(size-1) (size=1 → 1.0, asymptote 2.0)
            val multiplier = 2f - 0.5f.pow((list.size - 1).coerceAtLeast(0))
            val kindScore = weight(kind) * multiplier
            score += kindScore
            notes.add("${kind.name}×${list.size}")
        }

        // Cross-kind corroboration bonus: 3+ distinct kinds inside one window
        val distinctKinds = byKind.keys.size
        if (distinctKinds >= 3) {
            score += 0.15f * (distinctKinds - 2).coerceAtMost(3)
        }

        // Cap: indirect evidence can never claim certainty
        score = score.coerceIn(0f, 0.85f)

        if (score < 0.30f) return null

        // SIM state is context, not a signal — record but do not add weight
        val sim = currentSimState()

        val summary = buildString {
            append("Correlated ${observations.size} telephony observation(s), ")
            append("$distinctKinds distinct classes: ${notes.joinToString(", ")}. ")
            if (distinctKinds >= 3) append("Multi-class correlation inside one window. ")
            sim?.let { append("SIM state: $it. ") }
            append("Benign alternatives (network congestion, carrier maintenance, coverage) not excluded.")
        }

        return SilentSmsAssessment(
            timestampMs = effectiveNow,
            sourceClass = SourceClass.INDIRECT,
            confidence = score,
            sensorPath = "telephony-correlator/public-API",
            proofBoundary = SourceClass.INDIRECT.proofBoundary,
            observations = observations.toList(),
            exactEvent = null,
            anomalySummary = summary
        )
    }

    fun clear() = observations.clear()
}

/**
 * Hybrid facade. Combines the exact sensor (when privileged hardware and a
 * modem-diag capability exist) with the indirect correlator (always).
 * Evidence is labeled EXACT or INDIRECT per record — never merged into an
 * unlabeled stream.
 */
class HybridSilentSmsDetector(
    private val exactSensor: ExactSilentSmsSensor?,
    private val correlator: IndirectSilentSmsCorrelator
) {

    /**
     * Optional radio-log scanner (ADB READ_LOGS middle ground). When
     * supplied, pollLatest scans the radio buffer for silent-SMS markers.
     */
    var radioLogScanner: (() -> List<com.flockyou.privilege.RadioLogSilentSmsScanner.RadioLogHit>)? = null

    /** Radio-log hits already reported (dedupe by line). */
    private val reportedLogLines = mutableSetOf<String>()

    /**
     * Poll all paths. EXACT (modem diag) first, then EXACT_BY_LOG (radio
     * log), then INDIRECT if the correlator has one.
     */
    fun assess(nowMs: Long? = null): List<SilentSmsAssessment> {
        val results = mutableListOf<SilentSmsAssessment>()

        // EXACT_BY_LOG path — radio log named the event directly
        radioLogScanner?.invoke()?.forEach { hit ->
            if (reportedLogLines.add(hit.logLine)) {
                results.add(
                    SilentSmsAssessment(
                        timestampMs = hit.timestampMs,
                        sourceClass = SourceClass.EXACT_BY_LOG,
                        confidence = 0.80f,
                        sensorPath = "radio-log/${hit.matchedPattern}",
                        proofBoundary = SourceClass.EXACT_BY_LOG.proofBoundary,
                        observations = emptyList(),
                        exactEvent = ExactSilentSmsEvent(
                            timestampMs = hit.timestampMs,
                            sensorPath = "radio-log",
                            protocolDetail = hit.logLine
                        ),
                        anomalySummary = "Radio log matched silent-SMS pattern " +
                            "[${hit.matchedPattern}]: ${hit.logLine.take(120)}"
                    )
                )
            }
        }

        // EXACT path — only real when a sensor is present AND available
        val sensor = exactSensor
        if (sensor != null && sensor.isAvailable()) {
            sensor.pollLatest()?.let { event ->
                results.add(
                    SilentSmsAssessment(
                        timestampMs = event.timestampMs,
                        sourceClass = SourceClass.EXACT,
                        confidence = 0.95f,
                        sensorPath = event.sensorPath,
                        proofBoundary = SourceClass.EXACT.proofBoundary,
                        observations = emptyList(),
                        exactEvent = event,
                        anomalySummary = "Silent SMS (Type 0) directly observed by ${event.sensorPath}"
                    )
                )
            }
        }

        // INDIRECT path
        correlator.assess(nowMs = nowMs)?.let { results.add(it) }

        return results
    }

    fun observe(observation: TelephonyObservation) = correlator.observe(observation)
}

private fun Float.pow(n: Int): Float = Math.pow(this.toDouble(), n.toDouble()).toFloat()
