package com.inversionlabs.flocksucker.evidence

import android.database.sqlite.SQLiteDatabaseLockedException
import com.inversionlabs.flocksucker.data.model.Observation
import com.inversionlabs.flocksucker.data.model.ObservationIdentifierKind
import com.inversionlabs.flocksucker.data.model.ObservationProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationRecorderTest {
    @Test
    fun `successful insert is reported as recorded`() = runTest {
        val recorder = ObservationRecorder { _ -> Unit }
        val result = recorder.record(observation())

        assertEquals(ObservationRecordResult.Recorded("obs-1"), result)
    }

    @Test
    fun `coroutine cancellation is never converted into persistence failure`() = runTest {
        val recorder = ObservationRecorder { _ -> throw CancellationException("scan stopped") }

        var thrown: CancellationException? = null
        try {
            recorder.record(observation())
        } catch (error: CancellationException) {
            thrown = error
        }

        assertEquals("scan stopped", thrown?.message)
    }

    @Test
    fun `transient database lock is retried before evidence is dropped`() = runTest {
        var attempts = 0
        val recorder = ObservationRecorder { _ ->
            attempts += 1
            if (attempts < 3) throw SQLiteDatabaseLockedException("database is locked")
        }

        val result = recorder.record(observation())

        assertEquals(ObservationRecordResult.Recorded("obs-1"), result)
        assertEquals(3, attempts)
    }

    @Test
    fun `persistent database lock fails only after bounded retry budget`() = runTest {
        var attempts = 0
        val recorder = ObservationRecorder { _ ->
            attempts += 1
            throw SQLiteDatabaseLockedException("database is locked")
        }

        val result = recorder.record(observation())

        assertEquals(ObservationRecorder.MAX_LOCK_ATTEMPTS, attempts)
        assertTrue(result is ObservationRecordResult.Failed)
    }

    @Test
    fun `persistence failure is surfaced to caller`() = runTest {
        val failure = IllegalStateException("database unavailable")
        val recorder = ObservationRecorder { _ -> throw failure }
        val result = recorder.record(observation())
        val failed = result as ObservationRecordResult.Failed

        assertEquals("obs-1", failed.observationId)
        assertSame(failure, failed.error)
    }

    private fun observation() = Observation(
        id = "obs-1",
        sessionId = "session-1",
        timestamp = 1L,
        protocol = ObservationProtocol.BLUETOOTH_LE,
        sourceScanner = "TEST",
        observedIdentifier = "AA:BB:CC:DD:EE:FF",
        identifierKind = ObservationIdentifierKind.BLE_ADDRESS,
        rawPayloadSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        parserVersion = 1,
        schemaVersion = 1
    )
}
