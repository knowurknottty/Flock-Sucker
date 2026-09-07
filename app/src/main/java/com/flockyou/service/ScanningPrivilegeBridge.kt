package com.flockyou.service

import com.flockyou.scanner.ScannerModeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class WifiThrottleAttempt {
    NOT_ATTEMPTED,
    NOT_ELIGIBLE,
    APPLIED,
    FAILED,
    RESTORED,
    RESTORE_FAILED
}

data class ScanningPrivilegeEvidence(
    val privilegeMode: String = "UNKNOWN",
    val wifiThrottleEligible: Boolean = false,
    val wifiThrottleAttempt: WifiThrottleAttempt = WifiThrottleAttempt.NOT_ATTEMPTED,
    val wifiThrottleApplied: Boolean? = null,
    val error: String? = null
)

/**
 * Bridges runtime privilege evidence into the production ScanningService hot path.
 *
 * This class deliberately does not start its own scanner. It only applies narrowly
 * scoped privileged enhancements around the service's existing scan lifecycle, so
 * there can be no duplicate BLE/Wi-Fi scan loops or competing callbacks.
 */
@Singleton
class ScanningPrivilegeBridge @Inject constructor(
    private val scannerModeHelper: ScannerModeHelper
) {
    private val _evidence = MutableStateFlow(ScanningPrivilegeEvidence())
    val evidence: StateFlow<ScanningPrivilegeEvidence> = _evidence.asStateFlow()

    private var wifiThrottleChangedByBridge = false

    fun onScanningStarted(): ScanningPrivilegeEvidence {
        if (wifiThrottleChangedByBridge) return _evidence.value

        return try {
            val mode = scannerModeHelper.privilegeMode
            val eligible = mode.canDisableWifiThrottling

            if (!eligible) {
                ScanningPrivilegeEvidence(
                    privilegeMode = mode.toString(),
                    wifiThrottleEligible = false,
                    wifiThrottleAttempt = WifiThrottleAttempt.NOT_ELIGIBLE,
                    wifiThrottleApplied = null
                ).also { _evidence.value = it }
            } else {
                val applied = scannerModeHelper.disableWifiThrottling()
                wifiThrottleChangedByBridge = applied
                ScanningPrivilegeEvidence(
                    privilegeMode = mode.toString(),
                    wifiThrottleEligible = true,
                    wifiThrottleAttempt = if (applied) {
                        WifiThrottleAttempt.APPLIED
                    } else {
                        WifiThrottleAttempt.FAILED
                    },
                    wifiThrottleApplied = applied
                ).also { _evidence.value = it }
            }
        } catch (error: Exception) {
            wifiThrottleChangedByBridge = false
            ScanningPrivilegeEvidence(
                privilegeMode = "ERROR",
                wifiThrottleEligible = false,
                wifiThrottleAttempt = WifiThrottleAttempt.FAILED,
                wifiThrottleApplied = false,
                error = error.message ?: error::class.java.simpleName
            ).also { _evidence.value = it }
        }
    }

    fun onScanningStopped(): ScanningPrivilegeEvidence {
        if (!wifiThrottleChangedByBridge) return _evidence.value

        return try {
            val restored = scannerModeHelper.enableWifiThrottling()
            if (restored) wifiThrottleChangedByBridge = false
            _evidence.value.copy(
                wifiThrottleAttempt = if (restored) {
                    WifiThrottleAttempt.RESTORED
                } else {
                    WifiThrottleAttempt.RESTORE_FAILED
                },
                wifiThrottleApplied = if (restored) false else true,
                error = null
            ).also { _evidence.value = it }
        } catch (error: Exception) {
            _evidence.value.copy(
                wifiThrottleAttempt = WifiThrottleAttempt.RESTORE_FAILED,
                wifiThrottleApplied = true,
                error = error.message ?: error::class.java.simpleName
            ).also { _evidence.value = it }
        }
    }
}
