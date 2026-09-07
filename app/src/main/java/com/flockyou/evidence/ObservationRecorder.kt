package com.flockyou.evidence

import android.util.Log
import com.flockyou.data.model.Observation
import com.flockyou.data.repository.ObservationDao
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservationRecorder internal constructor(
    private val insertObservation: suspend (Observation) -> Unit
) {
    private val persistedCount = AtomicLong(0)
    @Inject
    constructor(observationDao: ObservationDao) : this({ observation -> observationDao.insert(observation) })

    suspend fun record(observation: Observation): ObservationRecordResult =
        try {
            insertObservation(observation)
            val count = persistedCount.incrementAndGet()
            if (count == 1L || count % 500L == 0L) {
                Log.d(
                    TAG,
                    "Evidence observation persisted count=$count protocol=${observation.protocol} id=${observation.id}"
                )
            }
            ObservationRecordResult.Recorded(observation.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ObservationRecordResult.Failed(observation.id, error)
        }

    companion object {
        private const val TAG = "ObservationRecorder"
    }
}

sealed interface ObservationRecordResult {
    val observationId: String

    data class Recorded(override val observationId: String) : ObservationRecordResult
    data class Failed(
        override val observationId: String,
        val error: Throwable
    ) : ObservationRecordResult
}