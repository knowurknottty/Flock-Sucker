package com.flockyou.service

data class WifiScanEvidence(
    val apiRequestCount: Long = 0,
    val apiAcceptedCount: Long = 0,
    val apiRejectedCount: Long = 0,
    val apiExceptionCount: Long = 0,
    val localSkipCount: Long = 0,
    val freshResultCount: Long = 0,
    val staleResultCount: Long = 0,
    val backoffLevel: Int = 0,
    val baseIntervalMs: Long = 0L,
    val adaptiveIntervalMs: Long = 0L,
    val lastRequestTimestampMs: Long? = null,
    val lastLocalSkipTimestampMs: Long? = null,
    val lastAcceptedTimestampMs: Long? = null,
    val lastRejectedTimestampMs: Long? = null,
    val lastFreshResultTimestampMs: Long? = null,
    val lastStaleResultTimestampMs: Long? = null
)

internal object WifiScanEvidenceReducer {
    fun localSkip(
        current: WifiScanEvidence,
        timestampMs: Long,
        baseIntervalMs: Long,
        adaptiveIntervalMs: Long,
        backoffLevel: Int
    ): WifiScanEvidence = current.copy(
        localSkipCount = current.localSkipCount + 1,
        backoffLevel = backoffLevel,
        baseIntervalMs = baseIntervalMs,
        adaptiveIntervalMs = adaptiveIntervalMs,
        lastLocalSkipTimestampMs = timestampMs
    )

    fun apiRequestResult(
        current: WifiScanEvidence,
        timestampMs: Long,
        started: Boolean,
        baseIntervalMs: Long,
        adaptiveIntervalMs: Long,
        backoffLevelAfter: Int
    ): WifiScanEvidence = current.copy(
        apiRequestCount = current.apiRequestCount + 1,
        apiAcceptedCount = current.apiAcceptedCount + if (started) 1 else 0,
        apiRejectedCount = current.apiRejectedCount + if (started) 0 else 1,
        backoffLevel = backoffLevelAfter,
        baseIntervalMs = baseIntervalMs,
        adaptiveIntervalMs = adaptiveIntervalMs,
        lastRequestTimestampMs = timestampMs,
        lastAcceptedTimestampMs = if (started) timestampMs else current.lastAcceptedTimestampMs,
        lastRejectedTimestampMs = if (started) current.lastRejectedTimestampMs else timestampMs
    )

    fun apiException(
        current: WifiScanEvidence,
        timestampMs: Long,
        baseIntervalMs: Long,
        adaptiveIntervalMs: Long,
        backoffLevel: Int
    ): WifiScanEvidence = current.copy(
        apiRequestCount = current.apiRequestCount + 1,
        apiExceptionCount = current.apiExceptionCount + 1,
        backoffLevel = backoffLevel,
        baseIntervalMs = baseIntervalMs,
        adaptiveIntervalMs = adaptiveIntervalMs,
        lastRequestTimestampMs = timestampMs
    )

    fun resultsBroadcast(
        current: WifiScanEvidence,
        timestampMs: Long,
        updated: Boolean,
        backoffLevelAfter: Int
    ): WifiScanEvidence = current.copy(
        freshResultCount = current.freshResultCount + if (updated) 1 else 0,
        staleResultCount = current.staleResultCount + if (updated) 0 else 1,
        backoffLevel = backoffLevelAfter,
        lastFreshResultTimestampMs = if (updated) timestampMs else current.lastFreshResultTimestampMs,
        lastStaleResultTimestampMs = if (updated) current.lastStaleResultTimestampMs else timestampMs
    )
}
