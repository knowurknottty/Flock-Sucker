package com.flockyou.service

enum class UltrasonicLifecycleState {
    IDLE,
    GATED,
    STARTING,
    PROVING,
    PROVEN,
    STALE,
    FAILED
}

object UltrasonicHealthPolicy {
    fun state(
        status: UltrasonicDetector.UltrasonicStatus?,
        nowMillis: Long = System.currentTimeMillis()
    ): UltrasonicLifecycleState {
        if (status == null) return UltrasonicLifecycleState.IDLE
        if (!status.lastError.isNullOrBlank()) return UltrasonicLifecycleState.FAILED
        if (!status.gateReason.isNullOrBlank()) return UltrasonicLifecycleState.GATED
        if (!status.isScanning) return UltrasonicLifecycleState.IDLE
        if (status.frameReadCount <= 0L) return UltrasonicLifecycleState.STARTING
        if (status.analysisCycleCount <= 0L || status.lastAnalysisTime == null) {
            return UltrasonicLifecycleState.PROVING
        }
        if (nowMillis - status.lastAnalysisTime > status.proofStaleAfterMs) {
            return UltrasonicLifecycleState.STALE
        }
        return UltrasonicLifecycleState.PROVEN
    }
}
