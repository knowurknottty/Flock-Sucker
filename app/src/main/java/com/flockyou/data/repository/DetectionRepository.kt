package com.flockyou.data.repository

import android.util.Log
import com.flockyou.data.model.*
import kotlinx.coroutines.flow.Flow
import com.flockyou.evidence.IdentityDecision
import com.flockyou.evidence.IdentityDecisionClass
import com.flockyou.evidence.IdentityResolver
import com.google.gson.Gson
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing detection data
 */
@Singleton
class DetectionRepository @Inject constructor(
    private val database: FlockYouDatabase,
    private val detectionDao: DetectionDao,
    private val sightingDao: SightingDao,
    private val identityLinkDao: IdentityLinkDao,
    private val deduplicator: DetectionDeduplicator,
    private val identityResolver: IdentityResolver
) {
    companion object {
        private const val TAG = "DetectionRepository"
        private const val IDENTITY_CANDIDATE_WINDOW_MS = 3600000L  // 1 hour
        private const val MAX_IDENTITY_LINKS_PER_DETECTION = 8
    }
    private val gson = Gson()
    val allDetections: Flow<List<Detection>> = detectionDao.getAllDetections()
    val activeDetections: Flow<List<Detection>> = detectionDao.getActiveDetections()
    val totalDetectionCount: Flow<Int> = detectionDao.getTotalDetectionCount()
    val highThreatCount: Flow<Int> = detectionDao.getHighThreatCount()
    val detectionsWithLocation: Flow<List<Detection>> = detectionDao.getDetectionsWithLocation()

    fun identityLinksForDetection(detectionId: String): Flow<List<IdentityLink>> =
        identityLinkDao.forSourceDetection(detectionId)
    
    fun getRecentDetections(sinceMillis: Long): Flow<List<Detection>> {
        return detectionDao.getRecentDetections(sinceMillis)
    }
    
    fun getDetectionsByThreatLevel(threatLevel: ThreatLevel): Flow<List<Detection>> {
        return detectionDao.getDetectionsByThreatLevel(threatLevel)
    }
    
    fun getDetectionsByDeviceType(deviceType: DeviceType): Flow<List<Detection>> {
        return detectionDao.getDetectionsByDeviceType(deviceType)
    }
    
    suspend fun getDetectionByMacAddress(macAddress: String): Detection? {
        return detectionDao.getDetectionByMacAddress(macAddress)
    }
    
    suspend fun getDetectionBySsid(ssid: String): Detection? {
        return detectionDao.getDetectionBySsid(ssid)
    }
    
    suspend fun getDetectionById(id: String): Detection? {
        return detectionDao.getDetectionById(id)
    }

    suspend fun getDetectionByServiceUuid(serviceUuid: String): Detection? {
        return detectionDao.getDetectionByServiceUuid(serviceUuid)
    }
    
    suspend fun getTotalDetectionCount(): Int {
        return detectionDao.getTotalDetectionCountSync()
    }

    suspend fun getAllDetectionsSnapshot(): List<Detection> {
        return detectionDao.getAllDetectionsSnapshot()
    }

    suspend fun getDetectionsBetween(startMillis: Long, endMillis: Long): List<Detection> {
        return detectionDao.getDetectionsBetween(startMillis, endMillis)
    }

    suspend fun insertDetection(detection: Detection) {
        detectionDao.insertDetection(detection)
    }
    
    suspend fun insertDetections(detections: List<Detection>) {
        detectionDao.insertDetections(detections)
    }
    
    suspend fun updateDetection(detection: Detection) {
        detectionDao.updateDetection(detection)
    }
    
    suspend fun deleteDetection(detection: Detection) {
        detectionDao.deleteDetection(detection)
    }
    
    suspend fun deleteAllDetections() {
        detectionDao.deleteAllDetections()
    }
    
    suspend fun deleteOldDetections(beforeMillis: Long) {
        detectionDao.deleteOldDetections(beforeMillis)
    }
    
    suspend fun markInactive(macAddress: String) {
        detectionDao.markInactive(macAddress)
    }
    
    suspend fun markOldInactive(beforeMillis: Long) {
        detectionDao.markOldInactive(beforeMillis)
    }
    
    /**
     * Update only when protocol-aware stable identity evidence permits it.
     * Weak SSID/UUID/name/manufacturer/RSSI similarity is recorded separately.
     */
    suspend fun upsertDetection(detection: Detection): Boolean {
        // Raw Observation evidence has already been written upstream. This layer may
        // throttle only an exact observed address; weak metadata never suppresses peers.
        if (deduplicator.shouldThrottle(detection)) {
            Log.d(TAG, "Throttled rapid exact-address detection: ${detection.macAddress}")
            return false
        }

        val candidates = identityCandidates(detection)
        val decisions = candidates.map { candidate ->
            candidate to identityResolver.resolve(detection, candidate)
        }
        val canonical = decisions
            .firstOrNull { (_, decision) -> decision.decision == IdentityDecisionClass.MATCH }

        return database.withTransaction {
            if (canonical != null) {
                val (existing, decision) = canonical
                detectionDao.updateDetection(
                    existing.copy(
                        lastSeenTimestamp = detection.timestamp,
                        rssi = detection.rssi,
                        latitude = detection.latitude ?: existing.latitude,
                        longitude = detection.longitude ?: existing.longitude,
                        seenCount = existing.seenCount + 1,
                        isActive = true
                    )
                )
                appendIdentityLink(detection, existing, decision, required = true)
                try {
                    recordSighting(
                        detectionId = existing.id,
                        detection = detection,
                        disposition = SightingDisposition.ACCEPTED_REPEAT
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Sighting ledger append failed for ${existing.id}: ${e.message}")
                }
                false
            } else {
                insertDetection(detection)
                decisions
                    .filter { (_, decision) -> decision.decision == IdentityDecisionClass.POSSIBLY_RELATED }
                    .sortedByDescending { (_, decision) -> decision.score }
                    .take(MAX_IDENTITY_LINKS_PER_DETECTION)
                    .forEach { (candidate, decision) -> appendIdentityLink(detection, candidate, decision) }
                try {
                    recordSighting(
                        detectionId = detection.id,
                        detection = detection,
                        disposition = SightingDisposition.NEW_DEVICE
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Sighting ledger append failed for ${detection.id}: ${e.message}")
                }
                true
            }
        }
    }

    private suspend fun identityCandidates(detection: Detection): List<Detection> {
        val candidates = linkedMapOf<String, Detection>()
        detection.macAddress?.let { getDetectionByMacAddress(it) }?.let { candidates[it.id] = it }
        detection.ssid?.let { getDetectionBySsid(it) }?.let { candidates[it.id] = it }
        detection.serviceUuids
            ?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            ?.let { getDetectionByServiceUuid(it) }
            ?.let { candidates[it.id] = it }
        detectionDao.getRecentDetectionsByType(
            deviceType = detection.deviceType.name,
            since = System.currentTimeMillis() - IDENTITY_CANDIDATE_WINDOW_MS
        ).forEach { candidates[it.id] = it }
        return candidates.values.toList()
    }

    private suspend fun appendIdentityLink(
        source: Detection,
        candidate: Detection,
        decision: IdentityDecision,
        required: Boolean = false
    ) {
        val storedDecision = when (decision.decision) {
            IdentityDecisionClass.MATCH -> IdentityLinkDecision.MATCH
            IdentityDecisionClass.POSSIBLY_RELATED -> IdentityLinkDecision.POSSIBLY_RELATED
            IdentityDecisionClass.DISTINCT -> IdentityLinkDecision.DISTINCT
        }
        try {
            identityLinkDao.insert(
                IdentityLink(
                    id = java.util.UUID.randomUUID().toString(),
                    sourceDetectionId = source.id,
                    candidateDetectionId = candidate.id,
                    sourceObservationId = source.sourceObservationId,
                    timestamp = source.timestamp,
                    decision = storedDecision,
                    ruleId = decision.ruleId,
                    score = decision.score,
                    evidenceJson = gson.toJson(decision.evidence),
                    rejectedAlternativesJson = gson.toJson(decision.rejectedAlternatives),
                    resolverVersion = decision.resolverVersion
                )
            )
        } catch (e: Exception) {
            if (required) throw e
            Log.w(TAG, "Identity-link append failed ${source.id} -> ${candidate.id}: ${e.message}")
        }
    }

    /**
     * Append one sighting row for an accepted observation. Sequence is
     * monotonic per detection. Location columns carry only what the
     * observation itself had (privacy filtering happens upstream at capture).
     */
    private suspend fun recordSighting(
        detectionId: String,
        detection: Detection,
        disposition: SightingDisposition
    ) {
        // Atomic next-sequence insert: no read-then-write race between
        // concurrent upserts for the same detection.
        sightingDao.insertWithNextSequence(
            id = java.util.UUID.randomUUID().toString(),
            detectionId = detectionId,
            timestamp = detection.timestamp,
            protocol = detection.protocol.name,
            sourceScanner = detection.detectionSource?.name
                ?: detection.protocol.name,
            detectorHealthGeneration = 0,
            rssi = detection.rssi,
            latitude = detection.latitude,
            longitude = detection.longitude,
            matchedRuleIds = detection.matchedPatterns,
            confidence = detection.threatScore.toFloat() / 100f,
            disposition = disposition.value(),
            sourceObservationId = detection.sourceObservationId
        )
    }

    // ==================== Sighting ledger accessors ====================

    fun sightingsForDetection(detectionId: String): Flow<List<com.flockyou.data.model.Sighting>> =
        sightingDao.forDetection(detectionId)

    fun locatedSightingsForDetection(detectionId: String): Flow<List<com.flockyou.data.model.Sighting>> =
        sightingDao.locatedForDetection(detectionId)

    val totalSightingCount: Flow<Long> = sightingDao.countAll()

    suspend fun recentSightings(detectionId: String, limit: Int = 50): List<com.flockyou.data.model.Sighting> =
        sightingDao.recentForDetection(detectionId, limit)


    /**
     * Update false positive analysis results for a detection
     */
    suspend fun updateFpAnalysis(
        detectionId: String,
        fpScore: Float?,
        fpReason: String?,
        fpCategory: String?,
        llmAnalyzed: Boolean
    ) {
        detectionDao.updateFpAnalysis(
            id = detectionId,
            fpScore = fpScore,
            fpReason = fpReason,
            fpCategory = fpCategory,
            analyzedAt = System.currentTimeMillis(),
            llmAnalyzed = llmAnalyzed
        )
    }

    /**
     * Get detections that haven't been analyzed for false positives yet
     */
    suspend fun getDetectionsPendingFpAnalysis(): List<Detection> {
        return detectionDao.getDetectionsPendingFpAnalysis()
    }

    /**
     * Get detections that haven't been analyzed for false positives yet (limited)
     */
    suspend fun getDetectionsPendingFpAnalysis(limit: Int): List<Detection> {
        return detectionDao.getDetectionsPendingFpAnalysis(limit)
    }

    /**
     * Mark a detection as reviewed (dismissed).
     * Sets isActive to false to indicate the user has acknowledged it.
     */
    suspend fun markAsReviewed(detectionId: String) {
        val detection = getDetectionById(detectionId) ?: return
        detectionDao.updateDetection(detection.copy(isActive = false))
        Log.d(TAG, "Detection marked as reviewed: $detectionId")
    }

    /**
     * Mark a detection as a false positive.
     * Sets fpScore to 1.0 (definitely false positive) and updates FP metadata.
     */
    suspend fun markAsFalsePositive(detectionId: String) {
        updateFpAnalysis(
            detectionId = detectionId,
            fpScore = 1.0f,
            fpReason = "User marked as false positive",
            fpCategory = "USER_REPORTED",
            llmAnalyzed = false
        )
        // Also mark as inactive since it's been reviewed
        markAsReviewed(detectionId)
        Log.d(TAG, "Detection marked as false positive: $detectionId")
    }

    /**
     * Return only detections connected by an explicit resolver decision.
     * Geographic proximity, shared vendor/type, SSID, UUID, and RSSI are not
     * silently promoted to identity relationships.
     */
    suspend fun getRelatedDetections(detection: Detection, limit: Int = 10): List<Detection> {
        val links = identityLinkDao.relatedTo(detection.id, limit * 2)
        val seenIds = mutableSetOf(detection.id)
        val related = mutableListOf<Detection>()
        for (link in links) {
            val otherId = if (link.sourceDetectionId == detection.id) {
                link.candidateDetectionId
            } else {
                link.sourceDetectionId
            }
            if (!seenIds.add(otherId)) continue
            val candidate = detectionDao.getDetectionById(otherId) ?: continue
            related += candidate
            if (related.size >= limit) break
        }
        Log.d(TAG, "Found ${related.size} resolver-linked detections for ${detection.id}")
        return related
    }

}
