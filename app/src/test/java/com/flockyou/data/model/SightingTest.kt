package com.flockyou.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Sighting evidence model and disposition semantics.
 *
 * Room migration itself requires an instrumented (on-device) database; these
 * unit tests pin the entity contract and disposition funnel semantics.
 */
class SightingTest {

    @Test
    fun `dispositions round-trip through lowercase values`() {
        for (d in SightingDisposition.entries) {
            assertEquals(d, SightingDisposition.from(d.value()))
        }
    }

    @Test
    fun `unknown disposition string maps to SUPPRESSED not crash`() {
        assertEquals(SightingDisposition.SUPPRESSED, SightingDisposition.from("garbage"))
        assertEquals(SightingDisposition.SUPPRESSED, SightingDisposition.from(""))
    }

    @Test
    fun `accepted and new dispositions are distinct evidence kinds`() {
        assertTrue(
            SightingDisposition.NEW_DEVICE.value() != SightingDisposition.ACCEPTED_REPEAT.value()
        )
        assertEquals("new_device", SightingDisposition.NEW_DEVICE.value())
        assertEquals("accepted_repeat", SightingDisposition.ACCEPTED_REPEAT.value())
    }

    @Test
    fun `sighting carries evidence fields separately from inference`() {
        val s = Sighting(
            id = "s-1",
            detectionId = "d-1",
            timestamp = 1000L,
            sequence = 2L,
            protocol = "WIFI",
            sourceScanner = "WIFI",
            detectorHealthGeneration = 3,
            rssi = -58,
            latitude = null, // location withheld = stays null, never fabricated
            longitude = null,
            accuracyMeters = null,
            matchedRuleIds = "[\"pattern-1\"]",
            confidence = 0.85f,
            rawMetadata = "{\"raw\":true}",
            disposition = "accepted_repeat",
            provenance = "{\"funnel\":\"candidates->accepted\"}"
        )
        assertEquals("d-1", s.detectionId)
        assertEquals(2L, s.sequence)
        assertEquals(null, s.latitude) // raw evidence: no location means null
        assertEquals("accepted_repeat", s.disposition)
    }
}
