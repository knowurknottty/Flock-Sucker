package com.flockyou.detection.enrichment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeslaSignaturesTest {

    @Test
    fun `canonical tesla vehicle advertisement name matches`() {
        assertTrue(TeslaSignatures.matchesName("S5d1f0a2b3c4e5f67C"))
        assertTrue(TeslaSignatures.matchesName("S0123456789ABCDEFc"))
    }

    @Test
    fun `tesla branded names match`() {
        assertTrue(TeslaSignatures.matchesName("Tesla_5F3A"))
        assertTrue(TeslaSignatures.matchesName("TESLA-Model-3"))
        assertTrue(TeslaSignatures.matchesName("Tesla 4B2C"))
    }

    @Test
    fun `non-tesla names do not match`() {
        assertFalse(TeslaSignatures.matchesName("MyPhone"))
        assertFalse(TeslaSignatures.matchesName("S1C"))           // too short
        assertFalse(TeslaSignatures.matchesName(null))
        assertFalse(TeslaSignatures.matchesName(""))
        assertFalse(TeslaSignatures.matchesName("Station-1"))
    }

    @Test
    fun `tesla oui prefixes match`() {
        assertTrue(TeslaSignatures.matchesOui("04:91:E7:11:22:33"))
        assertTrue(TeslaSignatures.matchesOui("98c135"))
    }

    @Test
    fun `non-tesla oui does not match`() {
        assertFalse(TeslaSignatures.matchesOui("00:11:22:33:44:55"))
    }

    @Test
    fun `confidence escalates with corroboration`() {
        // name + OUI = high
        assertEquals("high", TeslaSignatures.confidence("Tesla_1234", "04:91:E7:00:00:01"))
        // either alone = medium
        assertEquals("medium", TeslaSignatures.confidence("S0123456789ABCDEFc", null))
        assertEquals("medium", TeslaSignatures.confidence(null, "04:91:E7:00:00:01"))
        // neither = no tesla signal
        assertNull(TeslaSignatures.confidence("Phone", "00:11:22:33:44:55"))
        assertNull(TeslaSignatures.confidence(null, null))
    }
}
