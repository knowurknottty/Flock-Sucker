package com.flockyou.adversarial

import org.junit.Assert.*
import org.junit.Test

class BleMeshFrameCodecTest {
    @Test fun fragmentsAndReassemblesAtDefaultAttPayload() {
        val original = ByteArray(117) { (it * 17).toByte() }
        val frames = BleMeshFrameCodec.fragment(original, 0x1234, 20)
        assertTrue(frames.size > 1)
        assertTrue(frames.all { it.size <= 20 })
        val reassembler = BleMeshReassembler()
        var completed: ByteArray? = null
        frames.reversed().forEach { frame -> completed = reassembler.accept("peer", frame) ?: completed }
        assertArrayEquals(original, completed)
    }

    @Test fun rejectsMalformedOrMixedAssembly() {
        val r = BleMeshReassembler()
        assertNull(r.accept("p", byteArrayOf(1, 2, 3)))
        val a = BleMeshFrameCodec.fragment(ByteArray(40) { 1 }, 9, 20)
        val b = BleMeshFrameCodec.fragment(ByteArray(40) { 2 }, 9, 12)
        assertNull(r.accept("p", a.first()))
        assertNull(r.accept("p", b.first()))
    }
}
