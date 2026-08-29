package com.flockyou.adversarial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsBPositionTest {
    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun classicEvenOddPairDecodesGlobalPosition() {
        val evenMsg = hex("8D40621D58C382D690C8AC2863A7")
        val oddMsg = hex("8D40621D58C386435CC412692AD6")
        val even = AdsBFrameDecoder.airborneCpr(evenMsg, 1_457_996_402_000L)
        val odd = AdsBFrameDecoder.airborneCpr(oddMsg, 1_457_996_400_000L)
        assertNotNull(even)
        assertNotNull(odd)
        val position = AirborneCprDecoder.decodeGlobal(even!!, odd!!)
        assertNotNull(position)
        assertEquals(52.2572, position!!.latitude, 0.0002)
        assertEquals(3.91937, position.longitude, 0.0002)
    }

    @Test fun stalePairFailsClosed() {
        val even = AirborneCprFrame("ABCDEF", false, 93000, 51372, 0L)
        val odd = AirborneCprFrame("ABCDEF", true, 74158, 50194, 20_000L)
        assertNull(AirborneCprDecoder.decodeGlobal(even, odd))
    }
}
