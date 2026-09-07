package com.flockyou.data.repository

import androidx.room.Dao
import androidx.room.Query
import com.flockyou.data.model.Sighting
import kotlinx.coroutines.flow.Flow

/**
 * Append-only DAO for the sighting ledger. The only write path allocates the
 * per-detection sequence inside the INSERT statement; callers cannot split
 * sequence allocation from insertion. Sightings are never updated/upserted.
 */
@Dao
interface SightingDao {

    @Query("SELECT * FROM sightings WHERE detectionId = :detectionId ORDER BY timestamp ASC, sequence ASC")
    fun forDetection(detectionId: String): Flow<List<Sighting>>

    @Query("SELECT * FROM sightings WHERE detectionId = :detectionId AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY timestamp ASC, sequence ASC")
    fun locatedForDetection(detectionId: String): Flow<List<Sighting>>

    @Query("SELECT COUNT(*) FROM sightings")
    fun countAll(): Flow<Long>

    @Query("SELECT COUNT(*) FROM sightings WHERE detectionId = :detectionId")
    fun countForDetection(detectionId: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM sightings WHERE detectionId = :detectionId")
    suspend fun countForDetectionSync(detectionId: String): Long

    /**
     * Atomically append a sighting with the next monotonic per-detection
     * sequence. The sequence is computed inside the same INSERT statement, so
     * concurrent writers cannot collide on sequence.
     */
    @Query(
        "INSERT INTO sightings (id, detectionId, timestamp, sequence, protocol, sourceScanner, " +
        "detectorHealthGeneration, rssi, latitude, longitude, accuracyMeters, matchedRuleIds, " +
        "confidence, rawMetadata, disposition, provenance, sourceObservationId) " +
        "SELECT :id, :detectionId, :timestamp, COALESCE(MAX(sequence), 0) + 1, :protocol, :sourceScanner, " +
        ":detectorHealthGeneration, :rssi, :latitude, :longitude, NULL, :matchedRuleIds, " +
        ":confidence, NULL, :disposition, NULL, :sourceObservationId FROM sightings WHERE detectionId = :detectionId"
    )
    suspend fun insertWithNextSequence(
        id: String,
        detectionId: String,
        timestamp: Long,
        protocol: String,
        sourceScanner: String,
        detectorHealthGeneration: Long,
        rssi: Int?,
        latitude: Double?,
        longitude: Double?,
        matchedRuleIds: String?,
        confidence: Float?,
        disposition: String,
        sourceObservationId: String?
    )

    @Query("SELECT * FROM sightings WHERE detectionId = :detectionId ORDER BY timestamp DESC, sequence DESC LIMIT :limit")
    suspend fun recentForDetection(detectionId: String, limit: Int): List<Sighting>

    @Query("DELETE FROM sightings WHERE detectionId = :detectionId")
    suspend fun deleteForDetection(detectionId: String)
}
