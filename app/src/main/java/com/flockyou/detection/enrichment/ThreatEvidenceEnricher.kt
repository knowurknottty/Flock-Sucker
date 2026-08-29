package com.flockyou.detection.enrichment

/**
 * Pipeline-facing entry point for HIGH/CRITICAL WiFi evidence enrichment.
 * [HostProbeEngine] owns the pure decision surfaces and the connect-only
 * probe; [PROBE_ELIGIBLE] defines the threat levels that justify an active
 * probe (user-consented scope: "high and threat results").
 */
object ThreatEvidenceEnricher {

    /** Threat levels that justify an active port probe. */
    val PROBE_ELIGIBLE: Set<com.flockyou.data.model.ThreatLevel> =
        setOf(
            com.flockyou.data.model.ThreatLevel.HIGH,
            com.flockyou.data.model.ThreatLevel.CRITICAL
        )

    fun isProbeEligible(threatLevel: com.flockyou.data.model.ThreatLevel): Boolean =
        threatLevel in PROBE_ELIGIBLE
}
