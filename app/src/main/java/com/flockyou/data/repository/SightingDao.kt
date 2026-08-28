package com.flockyou.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flockyou.data.model.Sighting
import kotlinx.coroutines.flow.Flow

/**
 * Append-only DAO for the sighting ledger. Insert-only by design: sightings
 * are immutable evidence rows and are never updated or upserted in place.
 */
@Dao
interface SightingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sighting: Sighting)

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

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM sightings WHERE detectionId = :detectionId")
    suspend fun maxSequenceFor(detectionId: String): Long

    @Query("SELECT * FROM sightings WHERE detectionId = :detectionId ORDER BY timestamp DESC, sequence DESC LIMIT :limit")
    suspend fun recentForDetection(detectionId: String, limit: Int): List<Sighting>

    @Query("DELETE FROM sightings WHERE detectionId = :detectionId")
    suspend fun deleteForDetection(detectionId: String)
}
