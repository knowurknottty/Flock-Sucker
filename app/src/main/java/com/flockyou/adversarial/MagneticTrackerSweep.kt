package com.flockyou.adversarial

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class MagneticSweepState(
    val available: Boolean = false,
    val active: Boolean = false,
    val magnitudeMicroTesla: Float = 0f,
    val baselineMicroTesla: Float? = null,
    val deltaMicroTesla: Float = 0f,
    val peakDeltaMicroTesla: Float = 0f,
    val anomaly: Boolean = false,
    val samples: Int = 0,
    val message: String = "Idle"
)

class MagneticTrackerSweep(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val baselineSamples = ArrayDeque<Float>()
    private var lastProcessedTimestampNs = 0L
    private val _state = MutableStateFlow(MagneticSweepState(available = sensor != null))
    val state: StateFlow<MagneticSweepState> = _state.asStateFlow()

    fun start() {
        val s = sensor ?: run {
            _state.value = MagneticSweepState(available = false, message = "No magnetometer on this device")
            return
        }
        baselineSamples.clear()
        lastProcessedTimestampNs = 0L
        _state.value = MagneticSweepState(
            available = true,
            active = true,
            message = "Calibrating away from vehicle metal…"
        )
        manager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager.unregisterListener(this)
        _state.value = _state.value.copy(active = false, message = "Sweep stopped")
    }

    fun recalibrate() {
        baselineSamples.clear()
        lastProcessedTimestampNs = 0L
        _state.value = _state.value.copy(
            baselineMicroTesla = null,
            peakDeltaMicroTesla = 0f,
            anomaly = false,
            samples = 0,
            message = "Recalibrating…"
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD || !_state.value.active) return
        if (lastProcessedTimestampNs != 0L && event.timestamp - lastProcessedTimestampNs < 200_000_000L) return
        lastProcessedTimestampNs = event.timestamp
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        if (baselineSamples.size < 32) {
            baselineSamples.addLast(magnitude)
            val baseline = if (baselineSamples.size >= 12) median(baselineSamples) else null
            _state.value = _state.value.copy(
                magnitudeMicroTesla = magnitude,
                baselineMicroTesla = baseline,
                samples = baselineSamples.size,
                message = if (baseline == null) {
                    "Calibrating… ${baselineSamples.size}/12"
                } else {
                    "Baseline acquired; begin slow vehicle sweep"
                }
            )
            return
        }

        val baseline = _state.value.baselineMicroTesla ?: median(baselineSamples)
        val delta = kotlin.math.abs(magnitude - baseline)
        // Normal vehicle steel already distorts a compass. This threshold intentionally favors specificity.
        val threshold = maxOf(80f, baseline * 1.25f)
        val isAnomaly = delta >= threshold
        _state.value = _state.value.copy(
            magnitudeMicroTesla = magnitude,
            baselineMicroTesla = baseline,
            deltaMicroTesla = delta,
            peakDeltaMicroTesla = maxOf(_state.value.peakDeltaMicroTesla, delta),
            anomaly = isAnomaly,
            samples = _state.value.samples + 1,
            message = if (isAnomaly) {
                "Strong localized magnetic anomaly — inspect this physical area"
            } else {
                "Sweep slowly within a few centimeters of the surface"
            }
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun median(values: Collection<Float>): Float {
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2f else s[m]
    }
}
