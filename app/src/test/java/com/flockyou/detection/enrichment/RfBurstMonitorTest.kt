package com.flockyou.detection.enrichment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RfBurstMonitorTest {

    private fun monitor() = RfBurstMonitor(burstThresholdDb = -70)

    private fun feed(m: RfBurstMonitor, vararg samples: Pair<Long, Int>) {
        samples.forEach { (t, db) -> m.process(RfBurstMonitor.Sample(t, db)) }
    }

    @Test
    fun `single burst detected with peak and duration`() {
        val m = monitor()
        feed(m,
            0L to -90, 100L to -88,          // noise floor
            200L to -60, 250L to -52, 300L to -65,  // burst
            400L to -90                       // back to noise → burst closes
        )
        assertEquals(1, m.allBursts.size)
        val b = m.allBursts.first()
        assertEquals(200L, b.startMs)
        assertEquals(400L, b.endMs) // closes at first below-threshold sample
        assertEquals(-52, b.peakDb)
    }

    @Test
    fun `sub-minimum-duration blip is not a burst`() {
        val m = monitor()
        feed(m, 0L to -90, 100L to -60, 101L to -90, 200L to -90)
        // blip of 1ms < minBurstDurationMs(5)
        assertEquals(0, m.allBursts.size)
    }

    @Test
    fun `burst still open at flush is captured`() {
        val m = monitor()
        feed(m, 0L to -90, 100L to -55, 150L to -58)
        val b = m.flush(nowMs = 400L)
        assertNotNull(b)
        assertEquals(1, m.allBursts.size)
    }

    @Test
    fun `burst train with regular intervals is flagged`() {
        val m = monitor()
        // 5 bursts every 1000ms — metronomic beacon pattern
        var t = 0L
        repeat(5) {
            feed(m,
                t to -90,
                t + 10 to -50, t + 20 to -55, t + 30 to -90,
                t + 500 to -90
            )
            t += 1000
        }
        m.flush(t + 5000)
        val trains = m.detectTrains()
        assertTrue("expected a burst train", trains.isNotEmpty())
        val train = trains.first()
        assertTrue(train.burstCount >= 3)
        assertEquals(1000L, train.medianIntervalMs)
        assertTrue("regularity should be high", train.regularity > 0.5f)
    }

    @Test
    fun `irregular bursts do not form a train`() {
        val m = monitor()
        // bursts at wildly irregular intervals
        var t = 0L
        for (gap in listOf(100L, 5000L, 700L, 12000L, 300L)) {
            feed(m, t to -90, t + 10 to -50, t + 20 to -60, t + 30 to -90)
            t += gap
        }
        m.flush(t + 5000)
        assertTrue(m.allBursts.isNotEmpty())
        assertTrue("irregular pattern should not be a train", m.detectTrains().isEmpty())
    }

    @Test
    fun `evidence summary mentions train when present`() {
        val m = monitor()
        var t = 0L
        repeat(4) {
            feed(m, t to -90, t + 10 to -50, t + 25 to -90)
            t += 2000
        }
        m.flush(t + 2000)
        val summary = m.evidenceSummary()
        assertNotNull(summary)
        assertTrue(summary!!.contains("burst train"))
        assertTrue(summary.contains("possible beacon/pulsed emitter"))
    }

    @Test
    fun `no bursts no evidence`() {
        val m = monitor()
        feed(m, 0L to -90, 100L to -85, 200L to -90)
        m.flush(300L)
        assertNull(m.evidenceSummary())
    }

    @Test
    fun `reset clears state`() {
        val m = monitor()
        feed(m, 0L to -90, 100L to -50, 200L to -90, 300L to -90)
        m.reset()
        assertEquals(0, m.allBursts.size)
    }
}
