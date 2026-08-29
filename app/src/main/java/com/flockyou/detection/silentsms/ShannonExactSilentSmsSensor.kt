package com.flockyou.detection.silentsms

import com.flockyou.shannon.ShannonAnomaly
import com.flockyou.shannon.ShannonAnomalyType
import kotlinx.coroutines.flow.StateFlow

/**
 * Shannon modem-diag implementation of [ExactSilentSmsSensor]. Adapts the
 * existing ShannonDiagMonitor anomaly stream (Type-0 SMS records parsed by
 * NasMessageParser) into the generic exact-event abstraction.
 *
 * Availability is capability-bounded: the Shannon /diag channel exists only
 * on supported Samsung hardware and requires the modem-diag capability.
 */
class ShannonExactSilentSmsSensor(
    private val anomaliesFlow: StateFlow<List<ShannonAnomaly>>,
    private val modemDiagVerified: Boolean
) : ExactSilentSmsSensor {

    override fun isAvailable(): Boolean = modemDiagVerified

    override fun pollLatest(): ExactSilentSmsEvent? {
        val silentSms = anomaliesFlow.value.lastOrNull {
            it.type == ShannonAnomalyType.SILENT_SMS
        } ?: return null
        return ExactSilentSmsEvent(
            timestampMs = silentSms.timestamp,
            sensorPath = "shannon-diag/NasMessageParser Type-0 SMS-PP",
            protocolDetail = silentSms.description
        )
    }
}
