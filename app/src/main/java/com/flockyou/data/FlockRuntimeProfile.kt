package com.flockyou.data

/**
 * Typed app-side scan profile containing only settings with proven production consumers.
 * RF timing is intentionally absent: Android RF analysis is driven by Wi-Fi observations,
 * and the legacy RF interval preference has no acquisition loop to control.
 */
data class FlockRuntimeProfile(
    val wifiScanIntervalSeconds: Int,
    val bleScanDurationSeconds: Int,
    val inactiveTimeoutSeconds: Int,
    val enableBleScanning: Boolean,
    val enableWifiScanning: Boolean,
    val trackSeenDevices: Boolean,
    val ultrasonicScanIntervalSeconds: Int,
    val ultrasonicScanDurationSeconds: Int,
    val gnssAnomalyCooldownSeconds: Int,
    val satelliteCheckIntervalSeconds: Int,
    val cellularAnomalyCooldownSeconds: Int,
    val batteryAdaptiveMode: String,
    val autoBatteryAdaptive: Boolean,
    val flockBoostEnabled: Boolean
) {
    fun normalized(): FlockRuntimeProfile = copy(
        wifiScanIntervalSeconds = wifiScanIntervalSeconds.coerceIn(20, 120),
        bleScanDurationSeconds = bleScanDurationSeconds.coerceIn(5, 30),
        inactiveTimeoutSeconds = inactiveTimeoutSeconds.coerceIn(30, 300),
        ultrasonicScanIntervalSeconds = ultrasonicScanIntervalSeconds.coerceIn(15, 120),
        ultrasonicScanDurationSeconds = ultrasonicScanDurationSeconds.coerceIn(3, 15),
        gnssAnomalyCooldownSeconds = gnssAnomalyCooldownSeconds.coerceIn(60, 300),
        satelliteCheckIntervalSeconds = satelliteCheckIntervalSeconds.coerceIn(5, 60),
        cellularAnomalyCooldownSeconds = cellularAnomalyCooldownSeconds.coerceIn(1, 30),
        batteryAdaptiveMode = BatteryAdaptiveMode.entries
            .firstOrNull { it.id == batteryAdaptiveMode }
            ?.id ?: BatteryAdaptiveMode.BALANCED.id
    )

    companion object {
        fun from(settings: ScanSettings): FlockRuntimeProfile = FlockRuntimeProfile(
            wifiScanIntervalSeconds = settings.wifiScanIntervalSeconds,
            bleScanDurationSeconds = settings.bleScanDurationSeconds,
            inactiveTimeoutSeconds = settings.inactiveTimeoutSeconds,
            enableBleScanning = settings.enableBleScanning,
            enableWifiScanning = settings.enableWifiScanning,
            trackSeenDevices = settings.trackSeenDevices,
            ultrasonicScanIntervalSeconds = settings.ultrasonicScanIntervalSeconds,
            ultrasonicScanDurationSeconds = settings.ultrasonicScanDurationSeconds,
            gnssAnomalyCooldownSeconds = settings.gnssAnomalyCooldownSeconds,
            satelliteCheckIntervalSeconds = settings.satelliteScanIntervalSeconds,
            cellularAnomalyCooldownSeconds = settings.cellularAnomalyCooldownSeconds,
            batteryAdaptiveMode = settings.batteryAdaptiveMode,
            autoBatteryAdaptive = settings.autoBatteryAdaptive,
            flockBoostEnabled = settings.flockBoostEnabled
        ).normalized()
    }
}
