package com.flockyou.service

import com.flockyou.data.BatteryAdaptiveMode
import com.flockyou.data.ScanSettings

/**
 * Pure runtime policy for scanner admission and BLE aggressiveness.
 *
 * The default [ScanConfig] is deliberately conservative. Persisted
 * [ScanSettings] must be admitted before this mapper enables the capability
 * for LOW_LATENCY BLE. The capability is exercised only by explicit PERFORMANCE
 * mode or by an explicit runtime Flock Boost request.
 */
enum class BlePhyRequest { LE_1M, ALL_SUPPORTED }

data class BleControllerCapabilities(
    val extendedAdvertising: Boolean = false,
    val le2mPhy: Boolean = false,
    val codedPhy: Boolean = false
)

data class BleRuntimeScanPlan(
    val aggressive: Boolean,
    val reportDelayMs: Long,
    val aggressiveMatching: Boolean,
    val maxAdvertisementMatches: Boolean,
    val requestExtendedAdvertisements: Boolean,
    val phyRequest: BlePhyRequest
)

internal object ScanningRuntimePolicy {
    fun toRuntimeScanConfig(settings: ScanSettings): ScanConfig = ScanConfig(
        wifiScanInterval = settings.wifiScanIntervalSeconds * 1000L,
        bleScanDuration = settings.bleScanDurationSeconds * 1000L,
        inactiveTimeout = settings.inactiveTimeoutSeconds * 1000L,
        seenDeviceTimeout = settings.seenDeviceTimeoutMinutes * 60 * 1000L,
        enableBle = settings.enableBleScanning,
        enableWifi = settings.enableWifiScanning,
        trackSeenDevices = settings.trackSeenDevices,
        aggressiveBleMode = true
    )

    fun isBoostActive(
        manualBoostEnabled: Boolean,
        androidAutoClientCount: Int
    ): Boolean = manualBoostEnabled || androidAutoClientCount > 0

    fun shouldUseAggressiveBle(
        config: ScanConfig,
        batteryMode: BatteryAdaptiveMode,
        boostActive: Boolean = false
    ): Boolean = config.aggressiveBleMode && (
        boostActive || batteryMode == BatteryAdaptiveMode.PERFORMANCE
    )

    fun planBleScan(
        aggressive: Boolean,
        controller: BleControllerCapabilities
    ): BleRuntimeScanPlan = BleRuntimeScanPlan(
        aggressive = aggressive,
        reportDelayMs = if (aggressive) 0L else 500L,
        aggressiveMatching = aggressive,
        maxAdvertisementMatches = aggressive,
        requestExtendedAdvertisements = controller.extendedAdvertising,
        phyRequest = if (controller.extendedAdvertising && (controller.le2mPhy || controller.codedPhy)) {
            BlePhyRequest.ALL_SUPPORTED
        } else {
            BlePhyRequest.LE_1M
        }
    )

    fun shouldRestartCellularMonitoring(
        enabled: Boolean,
        monitorPresent: Boolean,
        anomalyJobActive: Boolean
    ): Boolean = enabled && monitorPresent && !anomalyJobActive
}
