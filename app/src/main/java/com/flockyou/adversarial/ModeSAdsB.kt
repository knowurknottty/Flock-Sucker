package com.flockyou.adversarial

import android.content.Context
import android.hardware.usb.UsbManager
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object ModeSNativeBridge {
    val isAvailable: Boolean get() = RtlSdrNativeBridge.isAvailable
    external fun nativeDemodulate(iq: ByteArray, validLength: Int): ByteArray
}

data class AdsBAircraft(
    val icao: String,
    val callsign: String? = null,
    val altitudeFeet: Int? = null,
    val groundSpeedKnots: Int? = null,
    val trackDegrees: Int? = null,
    val verticalRateFpm: Int? = null,
    val messageCount: Long = 0,
    val lastSeenMs: Long = 0L
)

data class AdsBReceiverState(
    val active: Boolean = false,
    val status: String = "Idle",
    val bytesReceived: Long = 0,
    val validFrames: Long = 0,
    val tuner: String? = null,
    val sampleRate: Double? = null,
    val centerHz: Double? = null,
    val aircraft: List<AdsBAircraft> = emptyList(),
    val error: String? = null
)

/** Decodes only CRC-valid DF17/DF18 frames emitted by the native PPM demodulator. */
object AdsBFrameDecoder {
    private const val CHARSET = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ#####_###############0123456789######"

    data class Update(
        val icao: String,
        val callsign: String? = null,
        val altitudeFeet: Int? = null,
        val groundSpeedKnots: Int? = null,
        val trackDegrees: Int? = null,
        val verticalRateFpm: Int? = null
    )

    fun crcValid(frame: ByteArray): Boolean {
        if (frame.size != 14) return false
        val df = (frame[0].toInt() and 0xff) ushr 3
        if (df != 17 && df != 18) return false
        var crc = 0
        for (bit in 0 until 88) {
            val input = ((frame[bit / 8].toInt() and 0xff) ushr (7 - bit % 8)) and 1
            val top = (crc ushr 23) and 1
            crc = (crc shl 1) and 0xffffff
            if ((top xor input) != 0) crc = crc xor 0xfff409
        }
        val parity = ((frame[11].toInt() and 0xff) shl 16) or
            ((frame[12].toInt() and 0xff) shl 8) or (frame[13].toInt() and 0xff)
        return crc == parity
    }

    fun airborneCpr(frame: ByteArray, timestampMs: Long): AirborneCprFrame? {
        if (!crcValid(frame)) return null
        val tc = bits(frame, 33, 5)
        if (tc !in 9..18) return null
        val icao = "%02X%02X%02X".format(
            Locale.US, frame[1].toInt() and 0xff, frame[2].toInt() and 0xff, frame[3].toInt() and 0xff
        )
        return AirborneCprFrame(
            icao = icao,
            odd = bits(frame, 54, 1) == 1,
            encodedLatitude = bits(frame, 55, 17),
            encodedLongitude = bits(frame, 72, 17),
            timestampMs = timestampMs
        )
    }

    private fun bits(frame: ByteArray, startBitOneBased: Int, length: Int): Int {
        var value = 0
        repeat(length) { offset ->
            val bit = startBitOneBased - 1 + offset
            val b = (frame[bit / 8].toInt() ushr (7 - bit % 8)) and 1
            value = (value shl 1) or b
        }
        return value
    }

    fun decode(frame: ByteArray): Update? {
        if (!crcValid(frame)) return null
        val icao = "%02X%02X%02X".format(
            Locale.US,
            frame[1].toInt() and 0xff,
            frame[2].toInt() and 0xff,
            frame[3].toInt() and 0xff
        )
        val tc = (frame[4].toInt() and 0xff) ushr 3
        return when (tc) {
            in 1..4 -> Update(icao = icao, callsign = decodeCallsign(frame))
            in 9..18 -> Update(icao = icao, altitudeFeet = decodeAltitude(frame))
            19 -> decodeVelocity(frame, icao)
            else -> Update(icao = icao)
        }
    }

    private fun decodeCallsign(m: ByteArray): String? {
        val b5 = m[5].toInt() and 0xff; val b6 = m[6].toInt() and 0xff
        val b7 = m[7].toInt() and 0xff; val b8 = m[8].toInt() and 0xff
        val b9 = m[9].toInt() and 0xff; val b10 = m[10].toInt() and 0xff
        val codes = intArrayOf(
            b5 ushr 2, ((b5 and 3) shl 4) or (b6 ushr 4), ((b6 and 15) shl 2) or (b7 ushr 6), b7 and 63,
            b8 ushr 2, ((b8 and 3) shl 4) or (b9 ushr 4), ((b9 and 15) shl 2) or (b10 ushr 6), b10 and 63
        )
        val value = codes.map { CHARSET.getOrElse(it) { '#' } }.joinToString("").replace('_', ' ').trim().replace("#", "")
        return value.takeIf { it.isNotBlank() }
    }

    private fun decodeAltitude(m: ByteArray): Int? {
        val code = (((m[5].toInt() and 0xff) shl 4) or ((m[6].toInt() and 0xff) ushr 4)) and 0xfff
        if ((code and 0x10) == 0) return null // Gillham Q=0 needs a different decoder; fail closed for now.
        val n = ((code and 0xfe0) ushr 1) or (code and 0x0f)
        return n * 25 - 1000
    }

    private fun decodeVelocity(m: ByteArray, icao: String): Update {
        val subtype = m[4].toInt() and 0x07
        if (subtype !in 1..2) return Update(icao)
        val b5 = m[5].toInt() and 0xff; val b6 = m[6].toInt() and 0xff
        val b7 = m[7].toInt() and 0xff; val b8 = m[8].toInt() and 0xff
        val b9 = m[9].toInt() and 0xff
        val ewRaw = ((b5 and 0x03) shl 8) or b6
        val nsRaw = ((b7 and 0x7f) shl 3) or (b8 ushr 5)
        if (ewRaw == 0 || nsRaw == 0) return Update(icao)
        val scale = if (subtype == 2) 4 else 1
        val ew = (ewRaw - 1) * scale * if ((b5 and 0x04) != 0) -1 else 1
        val ns = (nsRaw - 1) * scale * if ((b7 and 0x80) != 0) -1 else 1
        val speed = sqrt((ew * ew + ns * ns).toDouble()).toInt()
        var track = Math.toDegrees(atan2(ew.toDouble(), ns.toDouble()))
        if (track < 0) track += 360.0
        val vrRaw = ((b8 and 0x07) shl 6) or (b9 ushr 2)
        val vr = if (vrRaw == 0) null else (vrRaw - 1) * 64 * if ((b8 and 0x08) != 0) -1 else 1
        return Update(icao, groundSpeedKnots = speed, trackDegrees = track.toInt(), verticalRateFpm = vr)
    }
}

class RtlSdrAdsBReceiver(context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var radio: Rtl2832R8xxAndroidDevice? = null
    private val aircraft = LinkedHashMap<String, AdsBAircraft>()
    private val recentFrames = LinkedHashMap<String, Long>()
    private val _state = MutableStateFlow(AdsBReceiverState())
    val state: StateFlow<AdsBReceiverState> = _state.asStateFlow()

    fun start(device: android.hardware.usb.UsbDevice) {
        if (job?.isActive == true) return
        if (!ModeSNativeBridge.isAvailable) {
            _state.value = AdsBReceiverState(error = "Native Mode-S demodulator unavailable", status = "Native backend unavailable")
            return
        }
        job = scope.launch {
            val r = Rtl2832R8xxAndroidDevice(usbManager, device)
            radio = r
            val opened = r.openForAdsB().getOrElse { t ->
                _state.value = AdsBReceiverState(error = t.message ?: t.javaClass.simpleName, status = "RTL-SDR open/tune failed")
                radio = null
                return@launch
            }
            _state.value = AdsBReceiverState(
                active = true,
                status = "1090 MHz live · CRC-valid DF17/DF18 only",
                tuner = opened.tuner.name,
                sampleRate = opened.actualSampleRate,
                centerHz = opened.actualCenterHz
            )
            val input = ByteArray(131_072)
            var tail = ByteArray(0)
            try {
                while (isActive) {
                    val n = r.readIq(input)
                    if (n <= 0) continue
                    val combined = ByteArray(tail.size + n)
                    tail.copyInto(combined)
                    input.copyInto(combined, tail.size, 0, n)
                    val decoded = ModeSNativeBridge.nativeDemodulate(combined, combined.size)
                    val now = System.currentTimeMillis()
                    for (offset in decoded.indices step 14) {
                        if (offset + 14 > decoded.size) break
                        val frame = decoded.copyOfRange(offset, offset + 14)
                        val hex = frame.joinToString("") { "%02X".format(it.toInt() and 0xff) }
                        val prior = recentFrames[hex]
                        if (prior != null && now - prior < 1_000L) continue
                        recentFrames[hex] = now
                        val update = AdsBFrameDecoder.decode(frame) ?: continue
                        merge(update, now)
                    }
                    recentFrames.entries.removeAll { now - it.value > 2_000L }
                    aircraft.entries.removeAll { now - it.value.lastSeenMs > 120_000L }
                    val overlap = minOf(512, combined.size)
                    tail = combined.copyOfRange(combined.size - overlap, combined.size)
                    _state.value = _state.value.copy(
                        bytesReceived = _state.value.bytesReceived + n,
                        validFrames = aircraft.values.sumOf { it.messageCount },
                        aircraft = aircraft.values.sortedByDescending { it.lastSeenMs }.take(64)
                    )
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(active = false, error = t.message ?: t.javaClass.simpleName, status = "RTL-SDR receive failed")
            } finally {
                r.close()
                radio = null
                _state.value = _state.value.copy(active = false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        radio?.close()
        radio = null
        _state.value = _state.value.copy(active = false, status = "1090 MHz receiver stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun merge(u: AdsBFrameDecoder.Update, now: Long) {
        val old = aircraft[u.icao] ?: AdsBAircraft(icao = u.icao)
        aircraft[u.icao] = old.copy(
            callsign = u.callsign ?: old.callsign,
            altitudeFeet = u.altitudeFeet ?: old.altitudeFeet,
            groundSpeedKnots = u.groundSpeedKnots ?: old.groundSpeedKnots,
            trackDegrees = u.trackDegrees ?: old.trackDegrees,
            verticalRateFpm = u.verticalRateFpm ?: old.verticalRateFpm,
            messageCount = old.messageCount + 1,
            lastSeenMs = now
        )
    }
}
