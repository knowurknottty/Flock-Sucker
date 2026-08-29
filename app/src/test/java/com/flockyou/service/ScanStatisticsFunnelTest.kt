package com.flockyou.service

import com.flockyou.data.model.DetectionProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanStatisticsFunnelTest {

    @Test
    fun radioFunnel_tracksCandidatesAndPersistenceOutcomesByProtocol() {
        var stats = ScanStatistics(bleDevicesSeen = 9, wifiNetworksSeen = 14)

        stats = stats.recordCandidate(DetectionProtocol.BLUETOOTH_LE, 3)
        stats = stats.recordCandidate(DetectionProtocol.WIFI, 4)
        stats = stats.recordPersistenceOutcome(DetectionProtocol.BLUETOOTH_LE, isNew = true)
        stats = stats.recordPersistenceOutcome(DetectionProtocol.BLUETOOTH_LE, isNew = false)
        stats = stats.recordPersistenceOutcome(DetectionProtocol.WIFI, isNew = true)

        assertEquals(3, stats.bleCandidates)
        assertEquals(4, stats.wifiCandidates)
        assertEquals(1, stats.bleDetectionsCreated)
        assertEquals(1, stats.bleDetectionsNotNew)
        assertEquals(1, stats.wifiDetectionsCreated)
        assertEquals(0, stats.wifiDetectionsNotNew)
        assertEquals(2, stats.detectionsCreated)
    }

    @Test
    fun explicitWifiSuppression_isSeparateFromUnmatchedObservations() {
        val stats = ScanStatistics(wifiNetworksSeen = 20, wifiCandidates = 2)
            .recordExplicitWifiSuppressions(3)

        assertEquals(3, stats.wifiExplicitSuppressions)
        assertEquals(18, stats.wifiNonCandidateObservations)
    }

    @Test
    fun bleIngress_distinguishesRawCallbacksProcessedAndDropped() {
        val stats = ScanStatistics(bleDevicesSeen = 17)
            .recordBleIngress(received = 700, dropped = 23)

        assertEquals(700, stats.bleCallbacksReceived)
        assertEquals(23, stats.bleCallbacksDropped)
        assertEquals(17, stats.bleDevicesSeen)
    }
}
