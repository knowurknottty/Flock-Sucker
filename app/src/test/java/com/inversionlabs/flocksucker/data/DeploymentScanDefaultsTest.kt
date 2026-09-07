package com.inversionlabs.flocksucker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeploymentScanDefaultsTest {
    @Test
    fun `generic stock keeps portable defaults and boost off`() {
        val defaults = scanDefaultsForDeployment(constrained = false, defaultFlockBoost = false)
        assertEquals(25, defaults.wifiScanIntervalSeconds)
        assertEquals(10, defaults.bleScanDurationSeconds)
        assertFalse(defaults.flockBoostEnabled)
    }

    @Test
    fun `root targeted generic profile enables boost without pretending device is constrained`() {
        val defaults = scanDefaultsForDeployment(constrained = false, defaultFlockBoost = true)
        assertEquals(25, defaults.wifiScanIntervalSeconds)
        assertEquals(10, defaults.bleScanDurationSeconds)
        assertTrue(defaults.flockBoostEnabled)
    }

    @Test
    fun `tonga constrained defaults preserve measured low ram scan envelope and boost`() {
        val defaults = scanDefaultsForDeployment(constrained = true, defaultFlockBoost = true)
        assertEquals(45, defaults.wifiScanIntervalSeconds)
        assertEquals(8, defaults.bleScanDurationSeconds)
        assertEquals(60, defaults.rfScanIntervalSeconds)
        assertEquals(10, defaults.gnssScanIntervalSeconds)
        assertEquals(10, defaults.cellularScanIntervalSeconds)
        assertEquals("balanced", defaults.batteryAdaptiveMode)
        assertTrue(defaults.autoBatteryAdaptive)
        assertTrue(defaults.flockBoostEnabled)
    }
}
