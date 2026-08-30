package com.flockyou.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessBootstrapPolicyTest {
    @Test
    fun `package process is main`() {
        assertEquals(
            ProcessRole.MAIN,
            ProcessBootstrapPolicy.classify("com.flockyou.debug", "com.flockyou.debug")
        )
    }

    @Test
    fun `scanning process is secondary`() {
        assertEquals(
            ProcessRole.SECONDARY,
            ProcessBootstrapPolicy.classify("com.flockyou.debug:scanning", "com.flockyou.debug")
        )
    }

    @Test
    fun `missing process identity is unknown and conservative`() {
        assertEquals(ProcessRole.UNKNOWN, ProcessBootstrapPolicy.classify(null, "com.flockyou.debug"))
        assertEquals(ProcessRole.UNKNOWN, ProcessBootstrapPolicy.classify("", "com.flockyou.debug"))
    }
}
