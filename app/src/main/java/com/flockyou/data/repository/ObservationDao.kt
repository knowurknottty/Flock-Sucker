package com.flockyou.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flockyou.data.model.Observation
import kotlinx.coroutines.flow.Flow

/** Append-only access to authoritative raw observation evidence. */
@Dao
interface ObservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: Observation)

    @Query("SELECT * FROM observations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Observation?

    @Query("SELECT * FROM observations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun forSession(sessionId: String): Flow<List<Observation>>

    @Query("SELECT * FROM observations WHERE observedIdentifier = :identifier ORDER BY timestamp ASC")
    fun forObservedIdentifier(identifier: String): Flow<List<Observation>>

    @Query("SELECT * FROM observations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<Observation>

    @Query("SELECT COUNT(*) FROM observations")
    fun countAll(): Flow<Long>
}