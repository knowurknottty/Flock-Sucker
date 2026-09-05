package com.flockyou.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Append-only record of an identity resolver decision. */
@Entity(
    tableName = "identity_links",
    indices = [
        Index(value = ["sourceDetectionId"]),
        Index(value = ["candidateDetectionId"]),
        Index(value = ["sourceObservationId"]),
        Index(value = ["decision"]),
        Index(value = ["timestamp"])
    ]
)
data class IdentityLink(
    @PrimaryKey val id: String,
    val sourceDetectionId: String,
    val candidateDetectionId: String,
    val sourceObservationId: String? = null,
    val timestamp: Long,
    val decision: IdentityLinkDecision,
    val ruleId: String,
    val score: Float,
    val evidenceJson: String? = null,
    val rejectedAlternativesJson: String? = null,
    val resolverVersion: Int
) {
    init {
        require(id.isNotBlank()) { "IdentityLink id must not be blank" }
        require(sourceDetectionId.isNotBlank()) { "sourceDetectionId must not be blank" }
        require(candidateDetectionId.isNotBlank()) { "candidateDetectionId must not be blank" }
        require(ruleId.isNotBlank()) { "ruleId must not be blank" }
        require(score in 0f..1f) { "IdentityLink score must be between 0 and 1" }
        require(resolverVersion > 0) { "resolverVersion must be positive" }
    }
}

enum class IdentityLinkDecision { MATCH, POSSIBLY_RELATED, DISTINCT }
