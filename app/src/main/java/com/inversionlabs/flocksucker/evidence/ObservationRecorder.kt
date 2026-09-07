package com.inversionlabs.flocksucker.evidence

import android.database.sqlite.SQLiteDatabaseLockedException
import android.util.Log
import com.inversionlabs.flocksucker.data.model.Observation
import com.inversionlabs.flocksucker.data.repository.ObservationDao
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservationRecorder internal constructor(
    private val insertObservation: suspend (Observation) -> Unit
) {
    private val persistedCount = AtomicLong(0)
    @Inject
    constructor(observationDao: ObservationDao) : this({ observation -> observationDao.insert(observation) })

    suspend fun record(observation: Observation): ObservationRecordResult {
        var attempt = 1
        while (true) {
            try {
                insertObservation(observation)
                val count = persistedCount.incrementAndGet()
                if (count == 1L || count % 500L == 0L) {
                    Log.d(
                        TAG,
                        "Evidence observation persisted count=$count protocol=${observation.protocol} id=${observation.id}"
                    )
                }
                return ObservationRecordResult.Recorded(observation.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SQLiteDatabaseLockedException) {
                if (attempt >= MAX_LOCK_ATTEMPTS) {
                    return ObservationRecordResult.Failed(observation.id, error)
                }
                val retryDelayMs = LOCK_RETRY_BASE_DELAY_MS shl (attempt - 1)
                Log.w(
                    TAG,
                    "Transient database lock recording ${observation.id}; retry ${attempt + 1}/$MAX_LOCK_ATTEMPTS in ${retryDelayMs}ms"
                )
                delay(retryDelayMs)
                attempt += 1
            } catch (error: Throwable) {
                return ObservationRecordResult.Failed(observation.id, error)
            }
        }
    }

    companion object {
        private const val TAG = "ObservationRecorder"
        internal const val MAX_LOCK_ATTEMPTS = 4
        internal const val LOCK_RETRY_BASE_DELAY_MS = 25L
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