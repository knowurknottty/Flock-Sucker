package com.flockyou.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSettingsTest {
    @Test
    fun autoBattery_neverPromotesChargedDeviceToPerformance() {
        assertEquals(BatteryAdaptiveMode.BALANCED, BatteryAdaptiveMode.forBatteryLevel(100))
        assertEquals(BatteryAdaptiveMode.BALANCED, BatteryAdaptiveMode.forBatteryLevel(51))
    }

    @Test
    fun autoBattery_conservesAtLowBatteryThresholds() {
        assertEquals(BatteryAdaptiveMode.BATTERY_SAVER, BatteryAdaptiveMode.forBatteryLevel(30))
        assertEquals(BatteryAdaptiveMode.MINIMAL, BatteryAdaptiveMode.forBatteryLevel(15))
    }

    @Test
    fun manualPerformance_remainsAvailable() {
        val settings = ScanSettings(batteryAdaptiveMode = "performance", autoBatteryAdaptive = false)
        assertEquals(BatteryAdaptiveMode.PERFORMANCE, settings.getEffectiveMode(100))
    }

    @Test
    fun effectiveWifiInterval_respectsConfiguredBaseAndBatteryMode() {
        val balanced = ScanSettings(
            wifiScanIntervalSeconds = 45,
            batteryAdaptiveMode = "balanced",
            autoBatteryAdaptive = false
        )
        assertEquals(45, balanced.getEffectiveWifiInterval(100))

        val saver = balanced.copy(batteryAdaptiveMode = "battery_saver")
        assertEquals(67, saver.getEffectiveWifiInterval(100))

        val performance = balanced.copy(batteryAdaptiveMode = "performance")
        assertEquals(22, performance.getEffectiveWifiInterval(100))
    }

    @Test
    fun anomalyCooldownFields_areSemanticallyExplicitAndNormalized() {
        val settings = ScanSettings(
            gnssAnomalyCooldownSeconds = 10,
            cellularAnomalyCooldownSeconds = 0
        )

        assertEquals(60, settings.effectiveGnssAnomalyCooldownSeconds())
        assertEquals(1, settings.effectiveCellularAnomalyCooldownSeconds())
    }

    @Test
    fun defaultGnssAnomalyCooldown_matchesRuntimeMinimum() {
        assertEquals(60, ScanSettings().gnssAnomalyCooldownSeconds)
    }

    @Test
    fun runtimeProfile_normalizesOnlyEffectiveProductionKnobs() {
        val normalized = FlockRuntimeProfile(
            wifiScanIntervalSeconds = 1,
            bleScanDurationSeconds = 99,
            inactiveTimeoutSeconds = 1,
            enableBleScanning = true,
            enableWifiScanning = true,
            trackSeenDevices = true,
            ultrasonicScanIntervalSeconds = 10,
            ultrasonicScanDurationSeconds = 99,
            gnssAnomalyCooldownSeconds = 10,
            satelliteCheckIntervalSeconds = 1,
            cellularAnomalyCooldownSeconds = 0,
            batteryAdaptiveMode = "nonsense",
            autoBatteryAdaptive = false,
            flockBoostEnabled = true
        ).normalized()

        assertEquals(20, normalized.wifiScanIntervalSeconds)
        assertEquals(30, normalized.bleScanDurationSeconds)
        assertEquals(30, normalized.inactiveTimeoutSeconds)
        assertEquals(15, normalized.ultrasonicScanIntervalSeconds)
        assertEquals(15, normalized.ultrasonicScanDurationSeconds)
        assertEquals(60, normalized.gnssAnomalyCooldownSeconds)
        assertEquals(5, normalized.satelliteCheckIntervalSeconds)
        assertEquals(1, normalized.cellularAnomalyCooldownSeconds)
        assertEquals("balanced", normalized.batteryAdaptiveMode)
        assertEquals(true, normalized.flockBoostEnabled)
    }

    @Test
    fun scanSettings_doesNotAdvertiseDeadRfIntervalKnob() {
        val fieldNames = ScanSettings::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(false, fieldNames.contains("rfScanIntervalSeconds"))
    }

}
