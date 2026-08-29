package com.flockyou.adversarial

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.ArrayDeque
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OpticalPulseState(
    val active: Boolean = false,
    val candidateDetected: Boolean = false,
    val meanLuma: Float = 0f,
    val modulationDepth: Float = 0f,
    val estimatedHz: Float? = null,
    val confidence: Float = 0f,
    val sampleCount: Int = 0,
    val message: String = "Idle",
    val proofBoundary: String =
        "HEURISTIC / NOT WAVELENGTH PROOF: phone cameras cannot prove 850/940 nm or identify an ALPR by light alone"
)

data class LumaSample(
    val timestampNs: Long,
    val meanLuma: Float
)

class OpticalPulseWindow(
    private val maxSamples: Int = 64,
    private val minSamples: Int = 24
) {    private val samples = ArrayDeque<LumaSample>()

    fun add(sample: LumaSample): OpticalPulseState {
        samples.addLast(sample)
        while (samples.size > maxSamples) samples.removeFirst()
        if (samples.size < minSamples) {
            return OpticalPulseState(
                active = true,
                meanLuma = sample.meanLuma,
                sampleCount = samples.size,
                message = "Learning optical baseline… ${samples.size}/$minSamples"
            )
        }

        val values = samples.map { it.meanLuma }
        val mean = values.average().toFloat()
        val minimum = values.minOrNull() ?: mean
        val maximum = values.maxOrNull() ?: mean
        val modulation = ((maximum - minimum) / (mean + 1f)).coerceAtLeast(0f)
        val centered = values.map { it - mean }
        val rms = sqrt(centered.map { it * it }.average()).toFloat()
        val threshold = maxOf(2.5f, rms * 0.35f)

        var crossings = 0
        var previousSign = 0
        centered.forEach { value ->
            val sign = when {
                value > threshold -> 1
                value < -threshold -> -1
                else -> 0
            }
            if (sign != 0 && previousSign != 0 && sign != previousSign) crossings++
            if (sign != 0) previousSign = sign
        }

        val durationSeconds =
            (samples.last().timestampNs - samples.first().timestampNs) / 1_000_000_000.0
        val estimatedHz = if (durationSeconds > 0.45 && crossings >= 4) {
            (crossings / (2.0 * durationSeconds)).toFloat()
        } else null

        val lowAmbient = mean < 115f
        val rhythmic = estimatedHz != null && estimatedHz in 1.5f..30f
        val candidate = lowAmbient && rhythmic && modulation >= 0.12f && rms >= 4f
        val confidence = if (candidate) {
            (0.42f + modulation * 0.65f +
                ((crossings - 4).coerceAtMost(10) * 0.018f)).coerceAtMost(0.78f)
        } else 0f

        return OpticalPulseState(
            active = true,
            candidateDetected = candidate,
            meanLuma = mean,
            modulationDepth = modulation,
            estimatedHz = estimatedHz,
            confidence = confidence,
            sampleCount = samples.size,
            message = when {
                candidate -> "Rhythmic low-light illuminator candidate detected"
                !lowAmbient -> "Scene is bright; optical sweep is most discriminating in low ambient light"
                rhythmic -> "Rhythmic light modulation observed, below candidate threshold"
                else -> "Scanning for rhythmic low-light illumination"
            }
        )
    }

    fun clear() = samples.clear()
}
class NirPulseAnalyzer(
    private val window: OpticalPulseWindow = OpticalPulseWindow()
) : ImageAnalysis.Analyzer {
    private val _state = MutableStateFlow(
        OpticalPulseState(active = true, message = "Waiting for camera frames")
    )
    val state: StateFlow<OpticalPulseState> = _state.asStateFlow()

    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes.firstOrNull() ?: return
            val buffer = yPlane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            var total = 0L
            var count = 0
            var y = 4
            while (y < image.height) {
                var x = 4
                while (x < image.width) {
                    val index = y * rowStride + x * pixelStride
                    if (index in bytes.indices) {
                        total += bytes[index].toInt() and 0xff
                        count++
                    }
                    x += 8
                }
                y += 8
            }
            if (count > 0) {
                _state.value = window.add(
                    LumaSample(
                        timestampNs = image.imageInfo.timestamp,
                        meanLuma = total.toFloat() / count
                    )
                )
            }
        } finally {
            image.close()
        }
    }

    fun reset() {
        window.clear()
        _state.value = OpticalPulseState(
            active = true,
            message = "Optical baseline reset"
        )
    }
}