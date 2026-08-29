package com.flockyou.adversarial

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsBLoiterDetectorTest {
    @Test fun flagsSustainedClosedOrbitNearObserverAsHeuristic() {
        val detector = AdsBLoiterDetector(alertCooldownMs = 0L)
        val observerLat = 34.7304
        val observerLon = -86.5861
        val radiusM = 2_000.0
        var alert: OverheadLoiterCandidate? = null
        repeat(17) { i ->
            val angle = Math.toRadians(i * 22.5)
            val lat = observerLat + radiusM * cos(angle) / 111_320.0
            val lon = observerLon + radiusM * sin(angle) / (111_320.0 * cos(Math.toRadians(observerLat)))
            alert = detector.observe(
                "A0B1C2", AdsBPosition(lat, lon, i * 30_000L), observerLat, observerLon
            ) ?: alert
        }
        assertNotNull(alert)
        assertTrue(alert!!.cumulativeTurnDegrees >= 280.0)
        assertTrue(alert.proofBoundary.contains("not proof", ignoreCase = true))
    }

    @Test fun straightTransitDoesNotBecomeLoiter() {
        val detector = AdsBLoiterDetector(alertCooldownMs = 0L)
        val observerLat = 34.7304
        val observerLon = -86.5861
        var alert: OverheadLoiterCandidate? = null
        repeat(17) { i ->
            val lat = observerLat - 0.03 + i * 0.0035
            val lon = observerLon
            alert = detector.observe(
                "D0E1F2", AdsBPosition(lat, lon, i * 30_000L), observerLat, observerLon
            ) ?: alert
        }
        assertNull(alert)
    }
}
