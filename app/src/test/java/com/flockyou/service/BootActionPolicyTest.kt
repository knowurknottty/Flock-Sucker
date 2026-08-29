package com.flockyou.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootActionPolicyTest {
    @Test
    fun `only protected Android boot actions are trusted`() {
        assertTrue(BootActionPolicy.isTrustedBootAction("android.intent.action.BOOT_COMPLETED"))
        assertTrue(BootActionPolicy.isTrustedBootAction("android.intent.action.LOCKED_BOOT_COMPLETED"))

        assertFalse(BootActionPolicy.isTrustedBootAction("android.intent.action.QUICKBOOT_POWERON"))
        assertFalse(BootActionPolicy.isTrustedBootAction("com.htc.intent.action.QUICKBOOT_POWERON"))
        assertFalse(BootActionPolicy.isTrustedBootAction("com.example.SPOOF_BOOT"))
        assertFalse(BootActionPolicy.isTrustedBootAction(null))
    }
}
