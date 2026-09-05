package com.flockyou.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flockyou.data.model.IdentityLink
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityLinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(link: IdentityLink)

    @Query("SELECT * FROM identity_links WHERE sourceDetectionId = :sourceDetectionId ORDER BY timestamp ASC")
    fun forSourceDetection(sourceDetectionId: String): Flow<List<IdentityLink>>

    @Query("SELECT * FROM identity_links WHERE candidateDetectionId = :candidateDetectionId ORDER BY timestamp ASC")
    fun forCandidateDetection(candidateDetectionId: String): Flow<List<IdentityLink>>

    @Query("SELECT * FROM identity_links WHERE sourceObservationId = :observationId ORDER BY timestamp ASC")
    suspend fun forObservation(observationId: String): List<IdentityLink>

    @Query("""
        SELECT * FROM identity_links
        WHERE (sourceDetectionId = :detectionId OR candidateDetectionId = :detectionId)
          AND decision != 'DISTINCT'
        ORDER BY score DESC, timestamp DESC
        LIMIT :limit
    """)
    suspend fun relatedTo(detectionId: String, limit: Int): List<IdentityLink>
}
