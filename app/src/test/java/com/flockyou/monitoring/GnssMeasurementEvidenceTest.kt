package com.flockyou.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssMeasurementEvidenceTest {
    @Test
    fun registrationAndStatusAreRecordedWithoutClaimingDelivery() {
        val registered = GnssMeasurementEvidenceReducer.registrationResult(
            GnssMeasurementEvidence(), registered = true, timestampMs = 100L
        )
        val ready = GnssMeasurementEvidenceReducer.statusChanged(
            registered, status = "READY", timestampMs = 120L
        )

        assertTrue(ready.registrationAttempted)
        assertTrue(ready.callbackRegistered)
        assertEquals("READY", ready.callbackStatus)
        assertEquals(0L, ready.deliveryCount)
        assertFalse(ready.hasDeliveredMeasurements)
    }

    @Test
    fun batchesAccumulateFieldAvailabilityAndDeliveryCounts() {
        val first = GnssMeasurementEvidenceReducer.measurementBatch(
            GnssMeasurementEvidence(callbackRegistered = true),
            timestampMs = 200L,
            measurementCount = 9,
            codeLockedCount = 7,
            validAdrCount = 4,
            carrierFrequencyCount = 8,
            basebandCn0Count = 0,
            agcCount = 6
        )
        val second = GnssMeasurementEvidenceReducer.measurementBatch(
            first,
            timestampMs = 250L,
            measurementCount = 11,
            codeLockedCount = 10,
            validAdrCount = 5,
            carrierFrequencyCount = 11,
            basebandCn0Count = 9,
            agcCount = 8
        )

        assertEquals(2L, second.deliveryCount)
        assertEquals(200L, second.firstDeliveryTimestampMs)
        assertEquals(250L, second.lastDeliveryTimestampMs)
        assertEquals(11, second.lastMeasurementCount)
        assertEquals(10, second.lastCodeLockedCount)
        assertEquals(5, second.lastValidAdrCount)
        assertTrue(second.hasCarrierFrequency)
        assertTrue(second.hasBasebandCn0)
        assertTrue(second.hasAutomaticGainControl)
        assertTrue(second.hasDeliveredMeasurements)
    }
}
