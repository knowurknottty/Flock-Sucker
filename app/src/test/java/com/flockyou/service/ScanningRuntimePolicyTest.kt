package com.flockyou.service

import com.flockyou.data.BatteryAdaptiveMode
import com.flockyou.data.ScanSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime-policy regressions for the scanning service.
 *
 * These tests deliberately exercise pure service-owned policy so failures are
 * independent of Android framework timing and radio hardware.
 */
class ScanningRuntimePolicyTest {

    @Test
    fun defaultScanConfig_isConservativeUntilPersistedSettingsAreAdmitted() {
        assertFalse(
            "The pre-admission ScanConfig must never opt into aggressive/LOW_LATENCY BLE",
            ScanConfig().aggressiveBleMode
        )
    }

    @Test
    fun admittedSettings_preservePerformanceCapability() {
        val admitted = ScanningRuntimePolicy.toRuntimeScanConfig(ScanSettings())

        assertTrue(
            "Persisted/admitted scan settings should retain the capability for explicit PERFORMANCE mode",
            admitted.aggressiveBleMode
        )
    }

    @Test
    fun balancedMode_neverUsesAggressiveBle() {
        val admitted = ScanningRuntimePolicy.toRuntimeScanConfig(ScanSettings())

        assertFalse(
            "BALANCED mode must not use LOW_LATENCY BLE even after settings admission",
            ScanningRuntimePolicy.shouldUseAggressiveBle(admitted, BatteryAdaptiveMode.BALANCED)
        )
    }

    @Test
    fun explicitBoost_requestsAggressiveBleWithoutChangingBatteryMode() {
        val admitted = ScanningRuntimePolicy.toRuntimeScanConfig(ScanSettings())

        assertTrue(
            "Explicit Flock Boost should request the maximum-yield BLE mode even while battery mode remains BALANCED",
            ScanningRuntimePolicy.shouldUseAggressiveBle(
                admitted,
                BatteryAdaptiveMode.BALANCED,
                boostActive = true
            )
        )
        assertFalse(
            "Boost must not escalate the conservative pre-admission ScanConfig",
            ScanningRuntimePolicy.shouldUseAggressiveBle(
                ScanConfig(),
                BatteryAdaptiveMode.BALANCED,
                boostActive = true
            )
        )
    }

    @Test
    fun boostActivation_acceptsManualOrAndroidAutoSource() {
        assertFalse(ScanningRuntimePolicy.isBoostActive(false, 0))
        assertTrue(ScanningRuntimePolicy.isBoostActive(true, 0))
        assertTrue(ScanningRuntimePolicy.isBoostActive(false, 1))
        assertTrue(ScanningRuntimePolicy.isBoostActive(true, 2))
    }

    @Test
    fun disabledCellular_neverRequestsWatchdogRestart() {
        assertFalse(ScanningRuntimePolicy.shouldRestartCellularMonitoring(
            enabled = false, monitorPresent = true, anomalyJobActive = false
        ))
        assertTrue(ScanningRuntimePolicy.shouldRestartCellularMonitoring(
            enabled = true, monitorPresent = true, anomalyJobActive = false
        ))
    }

    @Test
    fun performanceMode_requiresSettingsAdmission() {
        assertFalse(
            "PERFORMANCE cannot escalate a pre-admission/default ScanConfig",
            ScanningRuntimePolicy.shouldUseAggressiveBle(
                ScanConfig(),
                BatteryAdaptiveMode.PERFORMANCE
            )
        )

        assertTrue(
            "Explicit PERFORMANCE may use aggressive BLE only after settings admission",
            ScanningRuntimePolicy.shouldUseAggressiveBle(
                ScanningRuntimePolicy.toRuntimeScanConfig(ScanSettings()),
                BatteryAdaptiveMode.PERFORMANCE
            )
        )
    }
}
