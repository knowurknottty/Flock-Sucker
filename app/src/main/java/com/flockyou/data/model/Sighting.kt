package com.flockyou.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only sighting ledger row: one accepted observation of a canonical
 * [Detection]. `Detection` remains the device/identity summary; each accepted
 * observation appends a Sighting so repeated evidence stays auditable.
 *
 * Disposition records the funnel decision that admitted this sighting:
 * accepted_repeat, new_device, throttled, suppressed, persistence_failed.
 * Throttled/suppressed rows carry funnel diagnostics only and never masquerade
 * as accepted evidence (see [SightingDisposition]).
 *
 * Privacy: location fields are stored only when existing privacy settings
 * permitted capture at observation time; they are otherwise null. The app
 * never fabricates coordinates for sightings without location.
 */
@Entity(
    tableName = "sightings",
    indices = [
        Index(value = ["detectionId"]),
        Index(value = ["timestamp"]),
        Index(value = ["detectionId", "timestamp"]),
        Index(value = ["sourceObservationId"])
    ]
)
data class Sighting(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "detectionId")
    val detectionId: String,
    val timestamp: Long,
    /** Monotonic per-detection sequence starting at 1, assigned at insert. */
    val sequence: Long,
    val protocol: String,
    /** Source scanner / lane that produced the observation (BLE, WIFI, ...). */
    val sourceScanner: String,
    /** Detector-health generation at observation time (proof-of-life linkage). */
    val detectorHealthGeneration: Long = 0,
    val rssi: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    /** Matched rule/pattern IDs that drove classification. */
    val matchedRuleIds: String? = null,
    val confidence: Float? = null,
    /** Raw observable metadata digest or payload (evidence, not inference). */
    val rawMetadata: String? = null,
    val disposition: String,
    /** Provenance needed to reconstruct the decision (JSON). */
    val provenance: String? = null,
    /** Exact immutable Observation row that produced this compatibility sighting. */
    val sourceObservationId: String? = null
)

/** Funnel dispositions for a sighting row. */
enum class SightingDisposition {
    NEW_DEVICE,
    ACCEPTED_REPEAT,
    THROTTLED,
    SUPPRESSED,
    PERSISTENCE_FAILED;

    fun value(): String = name.lowercase()

    companion object {
        fun from(value: String): SightingDisposition =
            entries.firstOrNull { it.value() == value.lowercase() } ?: SUPPRESSED
    }
}
