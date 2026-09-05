package com.flockyou.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDrivenDetectorLivenessTest {
    @Test
    fun `registered listener while monitoring is operational even during radio quiet`() {
        val liveness = EventDrivenDetectorLiveness(
            monitoring = true,
            listenerRegistered = true
        )
        assertTrue(liveness.isOperational)
    }

    @Test
    fun `missing listener is not operational`() {
        assertFalse(EventDrivenDetectorLiveness(true, false).isOperational)
        assertFalse(EventDrivenDetectorLiveness(false, true).isOperational)
    }
}
