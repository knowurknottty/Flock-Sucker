package com.flockyou.adversarial

import org.junit.Assert.*
import org.junit.Test

class AdsBFrameDecoderTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun validatesKnownDf17CrcAndAltitude() {
        val frame = hex("8D40621D58C382D690C8AC2863A7")
        assertTrue(AdsBFrameDecoder.crcValid(frame))
        val decoded = requireNotNull(AdsBFrameDecoder.decode(frame))
        assertEquals("40621D", decoded.icao)
        assertEquals(38000, decoded.altitudeFeet)
    }

    @Test fun decodesKnownIdentificationMessage() {
        val decoded = requireNotNull(AdsBFrameDecoder.decode(hex("8D4840D6202CC371C32CE0576098")))
        assertEquals("4840D6", decoded.icao)
        assertEquals("KLM1023", decoded.callsign)
    }

    @Test fun corruptedFrameFailsClosed() {
        val frame = hex("8D40621D58C382D690C8AC2863A7")
        frame[6] = (frame[6].toInt() xor 0x01).toByte()
        assertFalse(AdsBFrameDecoder.crcValid(frame))
        assertNull(AdsBFrameDecoder.decode(frame))
    }
}
