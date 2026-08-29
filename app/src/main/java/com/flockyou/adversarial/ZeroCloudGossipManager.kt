package com.flockyou.adversarial

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class MeshTransport { NONE, WIFI_AWARE, BLE_GATT }

/** Prefers Wi-Fi Aware and automatically drops to BLE GATT when NAN cannot become active. */
class ZeroCloudGossipManager(context: Context) {
    private val wifi = WifiAwareGossipManager(context)
    private val ble = BleGattGossipManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var startJob: Job? = null
    private var transport = MeshTransport.NONE
    private val _state = MutableStateFlow(MeshGossipState(supported = wifi.state.value.supported || ble.state.value.supported))
    val state: StateFlow<MeshGossipState> = _state.asStateFlow()

    init {
        scope.launch {
            wifi.state.collectLatest { state ->
                if (transport == MeshTransport.WIFI_AWARE) _state.value = state.copy(status = "Wi-Fi Aware · ${state.status}")
            }
        }
        scope.launch {
            ble.state.collectLatest { state ->
                if (transport == MeshTransport.BLE_GATT) _state.value = state.copy(status = "BLE GATT fallback · ${state.status}")
            }
        }
    }

    fun updateLocalTokens(tokens: Collection<String>) {
        wifi.updateLocalTokens(tokens)
        ble.updateLocalTokens(tokens)
    }

    fun start() {
        if (_state.value.active || startJob?.isActive == true) return
        startJob = scope.launch {
            if (wifi.state.value.supported) {
                transport = MeshTransport.WIFI_AWARE
                _state.value = wifi.state.value.copy(status = "Wi-Fi Aware · starting")
                wifi.start()
                delay(1_500L)
                if (wifi.state.value.active) return@launch
                wifi.stop()
            }
            transport = MeshTransport.BLE_GATT
            _state.value = ble.state.value.copy(status = "BLE GATT fallback · starting")
            ble.start()
        }
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        wifi.stop()
        ble.stop()
        transport = MeshTransport.NONE
        _state.value = _state.value.copy(active = false, peersSeen = 0, status = "Mesh stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
