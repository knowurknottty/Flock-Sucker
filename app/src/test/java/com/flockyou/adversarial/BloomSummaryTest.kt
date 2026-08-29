package com.flockyou.adversarial

import org.junit.Assert.*
import org.junit.Test

class BloomSummaryTest {
    @Test fun insertedTokensSurviveRoundTrip() {
        val tokens = setOf("camera-a", "camera-b", "camera-c")
        val original = BloomSummary.fromTokens(tokens)
        val encoded = original.toByteArray()
        assertEquals(BloomSummary.BYTE_COUNT, encoded.size)
        val restored = BloomSummary.fromBytes(encoded)
        tokens.forEach { assertTrue(restored.mightContain(it)) }
        assertEquals(original.population(), restored.population())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongSizedPayload() { BloomSummary.fromBytes(ByteArray(19)) }
}
