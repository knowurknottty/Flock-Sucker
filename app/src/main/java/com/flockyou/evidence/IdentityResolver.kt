package com.flockyou.evidence

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionProtocol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conservative physical-identity resolver.
 *
 * Weak radio similarity may create a POSSIBLY_RELATED decision, but never a
 * canonical identity match. MATCH is reserved for protocol-appropriate stable
 * identifiers available in the compatibility Detection projection.
 */
@Singleton
class IdentityResolver @Inject constructor() {
    fun resolve(left: Detection, right: Detection): IdentityDecision {
        if (left.protocol != right.protocol) {
            return IdentityDecision.distinct(
                ruleId = "PROTOCOL_MISMATCH",
                evidence = listOf("${left.protocol} != ${right.protocol}")
            )
        }

        val leftAddress = left.macAddress?.normalizeAddress()
        val rightAddress = right.macAddress?.normalizeAddress()
        if (leftAddress != null && rightAddress != null && leftAddress == rightAddress) {
            if (isStableAddressForCanonicalIdentity(left.protocol, leftAddress)) {
                return IdentityDecision.match(
                    ruleId = "EXACT_STABLE_ADDRESS",
                    evidence = listOf("protocol=${left.protocol}", "address=$leftAddress")
                )
            }
            return IdentityDecision.possiblyRelated(
                ruleId = "EXACT_UNSTABLE_ADDRESS",
                score = 0.60f,
                evidence = listOf("address=$leftAddress"),
                rejected = listOf("address is locally administered or BLE-random-compatible")
            )
        }
        val weakEvidence = mutableListOf<String>()
        var weakScore = 0f
        if (left.deviceType == right.deviceType) {
            weakEvidence += "deviceType=${left.deviceType}"
            weakScore += 0.15f
        }
        if (sameText(left.deviceName, right.deviceName)) {
            weakEvidence += "deviceName=${left.deviceName}"
            weakScore += 0.20f
        }
        if (sameText(left.manufacturer, right.manufacturer)) {
            weakEvidence += "manufacturer=${left.manufacturer}"
            weakScore += 0.15f
        }
        if (sameText(left.ssid, right.ssid)) {
            weakEvidence += "ssid=${left.ssid}"
            weakScore += 0.15f
        }
        val uuidOverlap = normalizedUuids(left).intersect(normalizedUuids(right))
        if (uuidOverlap.isNotEmpty()) {
            weakEvidence += "serviceUuidOverlap=${uuidOverlap.sorted().joinToString(",")}"
            weakScore += 0.20f
        }
        if (kotlin.math.abs(left.rssi - right.rssi) <= 15) {
            weakEvidence += "rssiProximity=${left.rssi}/${right.rssi}"
            weakScore += 0.10f
        }
        if (left.detectionMethod == right.detectionMethod) {
            weakEvidence += "method=${left.detectionMethod}"
            weakScore += 0.05f
        }

        return if (weakEvidence.isNotEmpty()) {
            IdentityDecision.possiblyRelated(
                ruleId = "WEAK_SIMILARITY_ONLY",
                score = weakScore.coerceAtMost(0.49f),
                evidence = weakEvidence,
                rejected = listOf("weak similarity is not canonical identity evidence")
            )
        } else {
            IdentityDecision.distinct(ruleId = "NO_IDENTITY_EVIDENCE")
        }
    }

    private fun isStableAddressForCanonicalIdentity(
        protocol: DetectionProtocol,
        normalized: String
    ): Boolean {
        val first = normalized.substringBefore(':').toIntOrNull(16) ?: return false
        val locallyAdministered = first and 0x02 != 0
        if (locallyAdministered) return false
        return when (protocol) {
            DetectionProtocol.WIFI -> true
            DetectionProtocol.BLUETOOTH_LE -> first and 0xC0 != 0xC0
            else -> false
        }
    }
    private fun String.normalizeAddress(): String? {
        val normalized = trim().uppercase()
        return normalized.takeIf {
            it.matches(Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$"))
        }
    }

    private fun sameText(left: String?, right: String?): Boolean =
        !left.isNullOrBlank() && !right.isNullOrBlank() && left.equals(right, ignoreCase = true)

    private fun normalizedUuids(detection: Detection): Set<String> =
        detection.serviceUuids
            ?.split(',')
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
}

enum class IdentityDecisionClass {
    MATCH,
    POSSIBLY_RELATED,
    DISTINCT
}

data class IdentityDecision(
    val decision: IdentityDecisionClass,
    val ruleId: String,
    val score: Float,
    val evidence: List<String> = emptyList(),
    val rejectedAlternatives: List<String> = emptyList(),
    val resolverVersion: Int = 1
) {    companion object {
        fun match(ruleId: String, evidence: List<String>) = IdentityDecision(
            decision = IdentityDecisionClass.MATCH,
            ruleId = ruleId,
            score = 1.0f,
            evidence = evidence
        )

        fun possiblyRelated(
            ruleId: String,
            score: Float,
            evidence: List<String>,
            rejected: List<String> = emptyList()
        ) = IdentityDecision(
            decision = IdentityDecisionClass.POSSIBLY_RELATED,
            ruleId = ruleId,
            score = score.coerceIn(0f, 0.99f),
            evidence = evidence,
            rejectedAlternatives = rejected
        )

        fun distinct(
            ruleId: String,
            evidence: List<String> = emptyList()
        ) = IdentityDecision(
            decision = IdentityDecisionClass.DISTINCT,
            ruleId = ruleId,
            score = 0f,
            evidence = evidence
        )
    }
}
