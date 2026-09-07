package com.flockyou.service

import com.flockyou.privilege.PrivilegeMode
import com.flockyou.scanner.ScannerModeHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanningPrivilegeBridgeTest {

    @Test
    fun start_attemptsWifiThrottleOnlyWhenRuntimeEvidenceAllowsIt() {
        val helper = mockk<ScannerModeHelper>(relaxed = true)
        every { helper.privilegeMode } returns PrivilegeMode.System(
            canDisableThrottling = true
        )
        every { helper.disableWifiThrottling() } returns true

        val bridge = ScanningPrivilegeBridge(helper)
        val evidence = bridge.onScanningStarted()

        verify(exactly = 1) { helper.disableWifiThrottling() }
        assertTrue(evidence.wifiThrottleEligible)
        assertEquals(WifiThrottleAttempt.APPLIED, evidence.wifiThrottleAttempt)
        assertTrue(evidence.wifiThrottleApplied == true)
    }

    @Test
    fun systemInstallWithoutGrant_doesNotAttemptHiddenWifiThrottleCall() {
        val helper = mockk<ScannerModeHelper>(relaxed = true)
        every { helper.privilegeMode } returns PrivilegeMode.System(
            hasPrivilegedPermissions = true,
            canDisableThrottling = false
        )

        val bridge = ScanningPrivilegeBridge(helper)
        val evidence = bridge.onScanningStarted()

        verify(exactly = 0) { helper.disableWifiThrottling() }
        assertFalse(evidence.wifiThrottleEligible)
        assertEquals(WifiThrottleAttempt.NOT_ELIGIBLE, evidence.wifiThrottleAttempt)
        assertNull(evidence.wifiThrottleApplied)
    }

    @Test
    fun stop_reenablesWifiThrottleOnlyAfterSuccessfulBridgeApply() {
        val helper = mockk<ScannerModeHelper>(relaxed = true)
        every { helper.privilegeMode } returns PrivilegeMode.System(
            canDisableThrottling = true
        )
        every { helper.disableWifiThrottling() } returns true
        every { helper.enableWifiThrottling() } returns true

        val bridge = ScanningPrivilegeBridge(helper)
        bridge.onScanningStarted()
        bridge.onScanningStopped()

        verify(exactly = 1) { helper.enableWifiThrottling() }
        assertEquals(WifiThrottleAttempt.RESTORED, bridge.evidence.value.wifiThrottleAttempt)
    }
}
