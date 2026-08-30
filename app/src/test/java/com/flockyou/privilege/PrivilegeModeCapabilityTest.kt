package com.flockyou.privilege

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeModeCapabilityTest {

    @Test
    fun systemCapabilities_followActualPermissionEvidence_notInstallLabel() {
        val systemWithoutPrivilegedGrants = PrivilegeMode.System()

        assertFalse(systemWithoutPrivilegedGrants.hasContinuousBleScan)
        assertFalse(systemWithoutPrivilegedGrants.canDisableWifiThrottling)
        assertFalse(systemWithoutPrivilegedGrants.hasRealMacAccess)
        assertFalse(systemWithoutPrivilegedGrants.hasPrivilegedPhoneAccess)
    }

    @Test
    fun bluetoothPrivilege_doesNotImplyWifiThrottlePrivilege() {
        val bluetoothOnly = PrivilegeMode.System(
            hasPrivilegedPermissions = true,
            canDisableThrottling = false,
            hasPeersMacPermission = false,
            hasReadPrivilegedPhoneState = false
        )

        assertTrue(bluetoothOnly.hasContinuousBleScan)
        assertFalse(bluetoothOnly.canDisableWifiThrottling)
    }

    @Test
    fun privilegedPhoneAccess_tracksActualReadPrivilegedPhoneStateGrant() {
        val phonePrivileged = PrivilegeMode.System(
            hasPrivilegedPermissions = false,
            canDisableThrottling = false,
            hasPeersMacPermission = false,
            hasReadPrivilegedPhoneState = true
        )

        assertTrue(phonePrivileged.hasPrivilegedPhoneAccess)
        assertFalse(phonePrivileged.hasContinuousBleScan)
    }
    @Test
    fun permissionEvidencePolicy_keepsCapabilityDomainsIndependent() {
        val bluetoothOnly = PrivilegeCapabilityPolicy.systemMode(
            PrivilegePermissionEvidence(
                bluetoothPrivileged = true,
                connectivityInternal = false,
                peersMac = false,
                localMac = false,
                readPrivilegedPhoneState = false
            )
        )
        assertTrue(bluetoothOnly.hasContinuousBleScan)
        assertFalse(bluetoothOnly.canDisableWifiThrottling)
        assertFalse(bluetoothOnly.hasPrivilegedPhoneAccess)

        val wifiAndPhone = PrivilegeCapabilityPolicy.systemMode(
            PrivilegePermissionEvidence(
                bluetoothPrivileged = false,
                connectivityInternal = true,
                peersMac = false,
                localMac = true,
                readPrivilegedPhoneState = true
            )
        )
        assertFalse(wifiAndPhone.hasContinuousBleScan)
        assertTrue(wifiAndPhone.canDisableWifiThrottling)
        assertTrue(wifiAndPhone.hasRealMacAccess)
        assertTrue(wifiAndPhone.hasPrivilegedPhoneAccess)
    }

}
