package com.flockyou.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiScanEvidenceTest {
    @Test
    fun evidenceSeparatesLocalSkipApiAcceptanceAndResultFreshness() {
        var evidence = WifiScanEvidence()

        evidence = WifiScanEvidenceReducer.apiRequestResult(
            evidence,
            timestampMs = 100L,
            started = false,
            baseIntervalMs = 20_000L,
            adaptiveIntervalMs = 20_000L,
            backoffLevelAfter = 1
        )
        evidence = WifiScanEvidenceReducer.localSkip(
            evidence,
            timestampMs = 110L,
            baseIntervalMs = 20_000L,
            adaptiveIntervalMs = 40_000L,
            backoffLevel = 1
        )
        evidence = WifiScanEvidenceReducer.apiRequestResult(
            evidence,
            timestampMs = 200L,
            started = true,
            baseIntervalMs = 20_000L,
            adaptiveIntervalMs = 40_000L,
            backoffLevelAfter = 1
        )
        evidence = WifiScanEvidenceReducer.resultsBroadcast(
            evidence,
            timestampMs = 250L,
            updated = false,
            backoffLevelAfter = 2
        )
        evidence = WifiScanEvidenceReducer.resultsBroadcast(
            evidence,
            timestampMs = 300L,
            updated = true,
            backoffLevelAfter = 0
        )

        assertEquals(2L, evidence.apiRequestCount)
        assertEquals(1L, evidence.apiAcceptedCount)
        assertEquals(1L, evidence.apiRejectedCount)
        assertEquals(1L, evidence.localSkipCount)
        assertEquals(110L, evidence.lastLocalSkipTimestampMs)
        assertEquals(1L, evidence.freshResultCount)
        assertEquals(1L, evidence.staleResultCount)
        assertEquals(0, evidence.backoffLevel)
        assertEquals(300L, evidence.lastFreshResultTimestampMs)
    }
}
