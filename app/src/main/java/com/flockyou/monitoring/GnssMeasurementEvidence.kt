package com.flockyou.monitoring

data class GnssMeasurementEvidence(
    val registrationAttempted: Boolean = false,
    val callbackRegistered: Boolean = false,
    val registrationTimestampMs: Long? = null,
    val callbackStatus: String = "UNKNOWN",
    val statusTimestampMs: Long? = null,
    val deliveryCount: Long = 0L,
    val firstDeliveryTimestampMs: Long? = null,
    val lastDeliveryTimestampMs: Long? = null,
    val lastMeasurementCount: Int = 0,
    val lastCodeLockedCount: Int = 0,
    val lastValidAdrCount: Int = 0,
    val hasCarrierFrequency: Boolean = false,
    val hasBasebandCn0: Boolean = false,
    val hasAutomaticGainControl: Boolean = false,
    val error: String? = null
) {
    val hasDeliveredMeasurements: Boolean
        get() = deliveryCount > 0L
}

internal object GnssMeasurementEvidenceReducer {
    fun registrationResult(
        current: GnssMeasurementEvidence,
        registered: Boolean,
        timestampMs: Long,
        error: String? = null
    ): GnssMeasurementEvidence = current.copy(
        registrationAttempted = true,
        callbackRegistered = registered,
        registrationTimestampMs = timestampMs,
        error = error
    )

    fun statusChanged(
        current: GnssMeasurementEvidence,
        status: String,
        timestampMs: Long
    ): GnssMeasurementEvidence = current.copy(
        callbackStatus = status,
        statusTimestampMs = timestampMs
    )

    fun measurementBatch(
        current: GnssMeasurementEvidence,
        timestampMs: Long,
        measurementCount: Int,
        codeLockedCount: Int,
        validAdrCount: Int,
        carrierFrequencyCount: Int,
        basebandCn0Count: Int,
        agcCount: Int
    ): GnssMeasurementEvidence = current.copy(
        callbackRegistered = true,
        deliveryCount = current.deliveryCount + 1L,
        firstDeliveryTimestampMs = current.firstDeliveryTimestampMs ?: timestampMs,
        lastDeliveryTimestampMs = timestampMs,
        lastMeasurementCount = measurementCount,
        lastCodeLockedCount = codeLockedCount,
        lastValidAdrCount = validAdrCount,
        hasCarrierFrequency = current.hasCarrierFrequency || carrierFrequencyCount > 0,
        hasBasebandCn0 = current.hasBasebandCn0 || basebandCn0Count > 0,
        hasAutomaticGainControl = current.hasAutomaticGainControl || agcCount > 0,
        error = null
    )
}
