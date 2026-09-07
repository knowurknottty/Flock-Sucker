package com.flockyou.service

enum class DetectorLifecycleState {
    REGISTERED,
    BLOCKED,
    STARTING,
    RUNNING,
    STALE,
    STOPPED,
    FAILED
}

data class DetectorHealthSummary(
    val totalCount: Int,
    val healthyCount: Int,
    val attentionCount: Int,
    val blockedCount: Int,
    val startingCount: Int,
    val staleCount: Int,
    val stoppedCount: Int,
    val failedCount: Int,
    val allSystemsOk: Boolean
)

object DetectorHealthPolicy {
    fun state(
        status: DetectorHealthStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): DetectorLifecycleState {
        if (!status.expectedToRun || !status.gateReason.isNullOrBlank()) {
            return DetectorLifecycleState.BLOCKED
        }
        if (!status.isRunning) {
            return when {
                !status.isHealthy -> DetectorLifecycleState.FAILED
                status.lastStartTime == null -> DetectorLifecycleState.REGISTERED
                else -> DetectorLifecycleState.STOPPED
            }
        }
        if (!status.isHealthy) return DetectorLifecycleState.FAILED

        val heartbeat = status.lastHeartbeatTime ?: status.lastSuccessfulScan
        if (heartbeat == null) return DetectorLifecycleState.STARTING
        if (nowMillis - heartbeat > status.staleThresholdMs) return DetectorLifecycleState.STALE
        return DetectorLifecycleState.RUNNING
    }

    fun isOperational(
        status: DetectorHealthStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = state(status, nowMillis) == DetectorLifecycleState.RUNNING

    fun summarize(
        statuses: Map<String, DetectorHealthStatus>,
        nowMillis: Long = System.currentTimeMillis()
    ): DetectorHealthSummary {
        val states = statuses.values.map { state(it, nowMillis) }
        val healthy = states.count { it == DetectorLifecycleState.RUNNING }
        val blocked = states.count { it == DetectorLifecycleState.BLOCKED }
        val starting = states.count { it == DetectorLifecycleState.STARTING }
        val stale = states.count { it == DetectorLifecycleState.STALE }
        val stopped = states.count { it == DetectorLifecycleState.STOPPED || it == DetectorLifecycleState.REGISTERED }
        val failed = states.count { it == DetectorLifecycleState.FAILED }
        val attention = blocked + starting + stale + stopped + failed

        return DetectorHealthSummary(
            totalCount = states.size,
            healthyCount = healthy,
            attentionCount = attention,
            blockedCount = blocked,
            startingCount = starting,
            staleCount = stale,
            stoppedCount = stopped,
            failedCount = failed,
            allSystemsOk = states.isNotEmpty() && attention == 0
        )
    }
}
