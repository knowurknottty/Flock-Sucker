package com.flockyou.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessBootstrapPolicyTest {
    @Test
    fun `package process is main`() {
        assertEquals(
            ProcessRole.MAIN,
            ProcessBootstrapPolicy.classify("com.flockyou.debug", "com.flockyou.debug")
        )
        assertTrue(ProcessBootstrapPolicy.shouldRunPackageBootstrap("com.flockyou.debug", "com.flockyou.debug"))
    }

    @Test
    fun `scanning process is secondary`() {
        assertEquals(
            ProcessRole.SECONDARY,
            ProcessBootstrapPolicy.classify("com.flockyou.debug:scanning", "com.flockyou.debug")
        )
        assertFalse(
            ProcessBootstrapPolicy.shouldRunPackageBootstrap("com.flockyou.debug:scanning", "com.flockyou.debug")
        )
    }

    @Test
    fun `missing process identity is unknown and conservative`() {
        assertEquals(ProcessRole.UNKNOWN, ProcessBootstrapPolicy.classify(null, "com.flockyou.debug"))
        assertEquals(ProcessRole.UNKNOWN, ProcessBootstrapPolicy.classify("", "com.flockyou.debug"))
        assertFalse(ProcessBootstrapPolicy.shouldRunPackageBootstrap(null, "com.flockyou.debug"))
    }

    @Test
    fun `AI worker scheduling requires AI and false positive filtering`() {
        assertEquals(AiBackgroundWorkAction.SCHEDULE, aiBackgroundWorkAction(true, true))
        assertEquals(AiBackgroundWorkAction.CANCEL, aiBackgroundWorkAction(true, false))
        assertEquals(AiBackgroundWorkAction.CANCEL, aiBackgroundWorkAction(false, true))
        assertEquals(AiBackgroundWorkAction.CANCEL, aiBackgroundWorkAction(false, false))
    }
}
