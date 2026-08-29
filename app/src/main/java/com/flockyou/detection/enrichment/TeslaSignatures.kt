package com.flockyou.detection.enrichment

/**
 * Tesla vehicle BLE identity signatures, cross-referenced alongside the
 * existing vehicle-command-service detection in BleDetectionHandler.
 *
 * Tesla BLE advertisements use rotating MACs (locally administered) and
 * deterministic name patterns; OUI attribution is therefore secondary
 * evidence here — the name/UUID patterns are primary.
 */
object TeslaSignatures {

    /** Tesla BLE advertised-name patterns (primary evidence). */
    val NAME_PATTERNS: List<Regex> = listOf(
        Regex("^S[0-9a-fA-F]{16}[Cc]$"),
        Regex("^(Tesla|TESLA)[-_ ].*"),
        Regex("^Tesla_[0-9A-Fa-f]{4,8}$")
    )

    /** Tesla OUI prefixes, compact hex form (no separators). */
    val OUI_PREFIXES_COMPACT: Set<String> = setOf(
        "D8714D", "0491E7", "98C135", "ACA89E"
    )

    /** Match a BLE device name against Tesla patterns. */
    fun matchesName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return NAME_PATTERNS.any { it.matches(name.trim()) }
    }

    /** OUI match (secondary evidence). Accepts AA:BB:CC, AA-BB-CC, or AABBCC. */
    fun matchesOui(macOrOui: String): Boolean {
        val compact = macOrOui.uppercase().replace(":", "").replace("-", "")
        if (compact.length < 6) return false
        return OUI_PREFIXES_COMPACT.contains(compact.take(6))
    }

    /**
     * Combined identity confidence: name+OUI = high, either alone = medium.
     * Returns null when no Tesla signal present.
     */
    fun confidence(name: String?, macOrOui: String?): String? {
        val nameHit = matchesName(name)
        val ouiHit = macOrOui != null && matchesOui(macOrOui)
        return when {
            nameHit && ouiHit -> "high"
            nameHit || ouiHit -> "medium"
            else -> null
        }
    }
}
