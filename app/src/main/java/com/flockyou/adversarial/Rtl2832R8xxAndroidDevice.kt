package com.flockyou.adversarial

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Android USB implementation of the RTL2832U + R820T/R820T2/R860 SDR path.
 *
 * Register programming is adapted from GNU Radio 4's MIT-licensed RTL2832Device implementation
 * (GNU Radio Authors / FAIR contributors). Android's UsbDeviceConnection is the only transport;
 * no libusb, usbfs path access, or root is used.
 */
class Rtl2832R8xxAndroidDevice(
    private val usbManager: UsbManager,
    val device: UsbDevice
) : AutoCloseable {
    companion object {
        const val ADSB_CENTER_HZ = 1_090_000_000.0
        const val ADSB_SAMPLE_RATE = 2_000_000
        private const val XTAL_HZ = 28_800_000
        private const val IF_HZ = 3_570_000
        private const val VCO_MIN = 1_770_000_000L
        private const val PLL_CAL_HZ = 56_000_000.0
        private const val CTRL_TIMEOUT_MS = 300
        private const val BULK_TIMEOUT_MS = 250
        private const val BLOCK_USB = 0x0100
        private const val BLOCK_SYS = 0x0200
        private const val BLOCK_IIC = 0x0600
        private const val WRITE_FLAG = 0x10
        private const val USB_SYSCTL = 0x2000
        private const val USB_EPA_CTL = 0x2148
        private const val USB_EPA_MAXPKT = 0x2158
        private const val DEMOD_CTL = 0x3000
        private const val DEMOD_CTL1 = 0x300B
        private const val R820T_ADDR = 0x34
        private const val R828D_ADDR = 0x74
        private const val R820T_ID = 0x69
        private const val SHADOW_START = 0x05
        private const val SHADOW_END = 0x1F

        private val INIT_REGS = intArrayOf(
            0x83,0x32,0x75,0xC0,0x40,0xD6,0x6C,0xF5,0x63,0x75,0x68,0x6C,0x83,0x80,
            0x00,0x0F,0x00,0xC0,0x30,0x48,0xCC,0x60,0x00,0x54,0xAE,0x4A,0xC0
        )
        private val FIR = intArrayOf(
            0xCA,0xDC,0xD7,0xD8,0xE0,0xF2,0x0E,0x35,0x06,0x50,0x9C,0x0D,0x71,0x11,
            0x14,0x71,0x74,0x19,0x41,0xA5
        )
        private val BIT_REV = intArrayOf(0x0,0x8,0x4,0xC,0x2,0xA,0x6,0xE,0x1,0x9,0x5,0xD,0x3,0xB,0x7,0xF)
        private data class Mux(val mhz: Int, val openDrain: Int, val rfMuxPoly: Int, val tfC: Int)
        private val MUX = listOf(
            Mux(0,0x08,0x02,0xDF), Mux(50,0x08,0x02,0xBE), Mux(55,0x08,0x02,0x8B),
            Mux(60,0x08,0x02,0x7B), Mux(65,0x08,0x02,0x69), Mux(70,0x08,0x02,0x58),
            Mux(75,0x00,0x02,0x44), Mux(80,0x00,0x02,0x44), Mux(90,0x00,0x02,0x34),
            Mux(100,0x00,0x02,0x34), Mux(110,0x00,0x02,0x24), Mux(120,0x00,0x02,0x24),
            Mux(140,0x00,0x02,0x14), Mux(180,0x00,0x02,0x13), Mux(220,0x00,0x02,0x13),
            Mux(250,0x00,0x02,0x11), Mux(280,0x00,0x02,0x00), Mux(310,0x00,0x41,0x00),
            Mux(450,0x00,0x41,0x00), Mux(588,0x00,0x40,0x00), Mux(650,0x00,0x40,0x00)
        )
    }

    enum class Tuner { R820T_FAMILY, R828D }
    data class OpenResult(val tuner: Tuner, val actualSampleRate: Double, val actualCenterHz: Double)

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpoint: UsbEndpoint? = null
    private var tuner: Tuner? = null
    private var tunerAddr = 0
    private val shadow = INIT_REGS.copyOf()

    @Synchronized
    fun openForAdsB(): Result<OpenResult> = runCatching {
        check(usbManager.hasPermission(device)) { "USB permission not granted" }
        check(device.interfaceCount > 0) { "RTL2832 has no USB interfaces" }
        val intf = device.getInterface(0)
        val bulkIn = (0 until intf.endpointCount)
            .map(intf::getEndpoint)
            .firstOrNull { it.direction == android.hardware.usb.UsbConstants.USB_DIR_IN && it.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK }
            ?: error("RTL2832 bulk-IN endpoint missing")
        val conn = usbManager.openDevice(device) ?: error("Unable to open authorized RTL2832 USB device")
        if (!conn.claimInterface(intf, true)) {
            conn.close()
            error("Unable to claim RTL2832 interface 0")
        }
        connection = conn
        usbInterface = intf
        endpoint = bulkIn
        try {
            initDemod()
            detectTuner()
            initR8xxTuner()
            val rate = setSampleRate(ADSB_SAMPLE_RATE.toDouble())
            val freq = setCenterFrequency(ADSB_CENTER_HZ)
            setTunerAutoGain()
            setDemodAgc(true)
            resetBuffer()
            OpenResult(requireNotNull(tuner), rate, freq)
        } catch (t: Throwable) {
            close()
            throw t
        }
    }

    fun readIq(buffer: ByteArray): Int {
        val conn = connection ?: return -1
        val ep = endpoint ?: return -1
        return conn.bulkTransfer(ep, buffer, buffer.size, BULK_TIMEOUT_MS)
    }

    @Synchronized
    override fun close() {
        val conn = connection
        val intf = usbInterface
        if (conn != null && intf != null) runCatching { conn.releaseInterface(intf) }
        runCatching { conn?.close() }
        connection = null
        usbInterface = null
        endpoint = null
        tuner = null
        tunerAddr = 0
    }

    private fun out(value: Int, index: Int, data: ByteArray) {
        val n = requireNotNull(connection).controlTransfer(0x40, 0, value, index, data, data.size, CTRL_TIMEOUT_MS)
        check(n == data.size) { "RTL2832 control OUT failed value=0x${value.toString(16)} index=0x${index.toString(16)} n=$n" }
    }

    private fun input(value: Int, index: Int, requested: Int): ByteArray {
        val buf = ByteArray(maxOf(requested, 8))
        val n = requireNotNull(connection).controlTransfer(0xC0, 0, value, index, buf, buf.size, CTRL_TIMEOUT_MS)
        check(n >= requested) { "RTL2832 control IN failed value=0x${value.toString(16)} index=0x${index.toString(16)} n=$n" }
        return buf.copyOf(requested)
    }

    private fun setUsbReg(addr: Int, value: Int, len: Int) {
        val data = if (len == 1) byteArrayOf(value.toByte()) else byteArrayOf((value ushr 8).toByte(), value.toByte())
        out(addr, BLOCK_USB or WRITE_FLAG, data)
    }

    private fun setSysReg(addr: Int, value: Int) = out(addr, BLOCK_SYS or WRITE_FLAG, byteArrayOf(value.toByte()))

    private fun setDemodReg(page: Int, addr: Int, value: Int, len: Int = 1) {
        val data = if (len == 1) byteArrayOf(value.toByte()) else byteArrayOf((value ushr 8).toByte(), value.toByte())
        out((addr shl 8) or 0x20, page or WRITE_FLAG, data)
        input(0x0120, 0x0A, 1) // required demod write read-back/flush
    }

    private fun openI2c() = setDemodReg(1, 0x01, 0x18)
    private fun closeI2c() = setDemodReg(1, 0x01, 0x10)

    private inline fun <T> withI2c(block: () -> T): T {
        openI2c()
        return try { block() } finally { runCatching { closeI2c() } }
    }

    private fun setI2cReg(addr: Int, reg: Int, value: Int) = out(addr, BLOCK_IIC or WRITE_FLAG, byteArrayOf(reg.toByte(), value.toByte()))

    private fun getI2cReg(addr: Int, reg: Int): Int {
        out(addr, BLOCK_IIC or WRITE_FLAG, byteArrayOf(reg.toByte()))
        return input(addr, BLOCK_IIC, 1)[0].toInt() and 0xff
    }

    private fun getI2cBuf(addr: Int, reg: Int, len: Int): ByteArray {
        out(addr, BLOCK_IIC or WRITE_FLAG, byteArrayOf(reg.toByte()))
        return input(addr, BLOCK_IIC, len)
    }

    private fun bitRev(v: Int): Int = (BIT_REV[v and 0xf] shl 4) or BIT_REV[(v ushr 4) and 0xf]
    private fun readTunerBuf(reg: Int, len: Int): IntArray = getI2cBuf(tunerAddr, reg, len).map { bitRev(it.toInt() and 0xff) }.toIntArray()

    private fun writeTunerMask(reg: Int, value: Int, mask: Int) {
        val idx = reg - SHADOW_START
        require(idx in shadow.indices) { "R8xx register out of shadow range: 0x${reg.toString(16)}" }
        shadow[idx] = (shadow[idx] and mask.inv()) or (value and mask)
        setI2cReg(tunerAddr, reg, shadow[idx])
    }
    private fun writeTuner(reg: Int, value: Int) = writeTunerMask(reg, value, 0xff)

    private fun initDemod() {
        setUsbReg(USB_SYSCTL, 0x09, 1)
        setUsbReg(USB_EPA_MAXPKT, 0x0002, 2)
        setUsbReg(USB_EPA_CTL, 0x1002, 2)
        setSysReg(DEMOD_CTL1, 0x22)
        setSysReg(DEMOD_CTL, 0xE8)
        setDemodReg(1, 0x01, 0x14)
        setDemodReg(1, 0x01, 0x10)
        setDemodReg(1, 0x15, 0x00)
        for (off in 0x16..0x1B) setDemodReg(1, off, 0x00)
        FIR.forEachIndexed { i, value -> setDemodReg(1, 0x1C + i, value) }
        setDemodReg(0, 0x19, 0x05)
        setDemodReg(1, 0x93, 0xF0)
        setDemodReg(1, 0x94, 0x0F)
        setDemodReg(1, 0x11, 0x00)
        setDemodReg(1, 0x04, 0x00)
        setDemodReg(0, 0x61, 0x60)
        setDemodReg(0, 0x06, 0x80)
        setDemodReg(1, 0xB1, 0x1B)
        setDemodReg(0, 0x0D, 0x83)
    }

    private fun detectTuner() {
        withI2c {
            when {
                getI2cReg(R820T_ADDR, 0x00) == R820T_ID -> { tuner = Tuner.R820T_FAMILY; tunerAddr = R820T_ADDR }
                getI2cReg(R828D_ADDR, 0x00) == R820T_ID -> { tuner = Tuner.R828D; tunerAddr = R828D_ADDR }
                else -> error("Unsupported RTL2832 tuner; R820T/R820T2/R860/R828D required")
            }
        }
        setDemodReg(1, 0xB1, 0x1A)
        setDemodReg(0, 0x08, 0x4D)
        setDemodReg(1, 0x15, 0x01)
    }

    private fun initR8xxTuner() {
        INIT_REGS.copyInto(shadow)
        withI2c {
            shadow.forEachIndexed { i, value -> setI2cReg(tunerAddr, SHADOW_START + i, value) }
            val writes = arrayOf(
                intArrayOf(0x0C,0x00,0x0F), intArrayOf(0x13,0x03,0x03), intArrayOf(0x1D,0x00,0x38),
                intArrayOf(0x1C,0x00,0xF8), intArrayOf(0x06,0x10,0x10), intArrayOf(0x1A,0x30,0x30),
                intArrayOf(0x1D,0xE5,0xC7), intArrayOf(0x1C,0x24,0xF8), intArrayOf(0x0D,0x53,0xFF),
                intArrayOf(0x0E,0x75,0xFF), intArrayOf(0x05,0x00,0x60), intArrayOf(0x06,0x00,0x08),
                intArrayOf(0x11,0x38,0x38), intArrayOf(0x17,0x30,0x30), intArrayOf(0x0A,0x40,0x60),
                intArrayOf(0x1E,0x00,0x60)
            )
            writes.forEach { writeTunerMask(it[0], it[1], it[2]) }
        }
        setPll(PLL_CAL_HZ)
        withI2c {
            writeTunerMask(0x0B, 0x10, 0x10)
            writeTunerMask(0x0B, 0x00, 0x10)
            writeTunerMask(0x0F, 0x00, 0x08)
        }
    }

    private fun setSampleRate(rate: Double): Double {
        require(rate in 900_001.0..3_200_000.0) { "Unsupported RTL2832 sample rate $rate" }
        val ratio = (((XTAL_HZ.toDouble() * (1L shl 22)) / rate).toLong() and 0x0FFFFFFCL).toInt()
        val real = (XTAL_HZ.toDouble() * (1L shl 22)) / (ratio.toLong() and 0xffffffffL).toDouble()
        setDemodReg(1, 0x9F, ratio ushr 16, 2)
        setDemodReg(1, 0xA1, ratio and 0xffff, 2)
        setDemodReg(1, 0x01, 0x14)
        setDemodReg(1, 0x01, 0x10)
        return real
    }

    private fun setCenterFrequency(freq: Double): Double {
        val tunerFreq = freq + IF_HZ
        setMux(tunerFreq)
        val pll = setPll(tunerFreq)
        val actual = pll - IF_HZ
        val ifMul = ((-IF_HZ.toDouble() * (1L shl 22) / XTAL_HZ).toInt()) and 0x3fffff
        setDemodReg(1, 0x19, (ifMul ushr 16) and 0x3f)
        setDemodReg(1, 0x1A, (ifMul ushr 8) and 0xff)
        setDemodReg(1, 0x1B, ifMul and 0xff)
        return actual
    }

    private fun setPll(freq: Double): Double {
        var divNum = 0
        var mixDiv = 2L
        while (mixDiv <= 64 && freq * mixDiv < VCO_MIN) { mixDiv *= 2; divNum++ }
        divNum = divNum.coerceIn(0, 6)
        mixDiv = 1L shl (divNum + 1)

        val fine = withI2c { (readTunerBuf(0x00, 5)[4] and 0x30) ushr 4 }
        val ref = if (tuner == Tuner.R828D) 1 else 2
        if (fine > ref) divNum-- else if (fine < ref) divNum++
        divNum = divNum.coerceIn(0, 6)
        mixDiv = 1L shl (divNum + 1)

        return withI2c {
            writeTunerMask(0x10, divNum shl 5, 0xE0)
            val vcoFreq = freq * mixDiv
            var nint = (vcoFreq / (2.0 * XTAL_HZ)).toLong().coerceAtLeast(13)
            val vcoFra = vcoFreq - 2.0 * XTAL_HZ * nint
            val ni = ((nint - 13) / 4).toInt()
            val si = ((nint - 13) % 4).toInt()
            writeTuner(0x14, ni or (si shl 6))
            val sdm = min(65535.0, 32768.0 * vcoFra / XTAL_HZ).toInt()
            writeTunerMask(0x12, if (sdm == 0) 0x08 else 0x00, 0x08)
            writeTuner(0x16, (sdm ushr 8) and 0xff)
            writeTuner(0x15, sdm and 0xff)
            val status = readTunerBuf(0x00, 3)
            if ((status[2] and 0x40) == 0) writeTunerMask(0x12, 0x60, 0xE0)
            (2.0 * XTAL_HZ * (nint + sdm / 65536.0)) / mixDiv
        }
    }

    private fun setMux(freq: Double) {
        val mhz = (freq / 1e6).toInt()
        val cfg = MUX.lastOrNull { it.mhz <= mhz } ?: MUX.first()
        withI2c {
            writeTunerMask(0x17, cfg.openDrain, 0x08)
            writeTunerMask(0x1A, cfg.rfMuxPoly, 0xC3)
            writeTunerMask(0x1B, cfg.tfC, 0xFF)
            writeTunerMask(0x10, 0x00, 0x0B)
            writeTunerMask(0x08, 0x00, 0x3F)
            writeTunerMask(0x09, 0x00, 0x3F)
        }
    }

    private fun setTunerAutoGain() = withI2c {
        writeTunerMask(0x05, 0x00, 0x10)
        writeTunerMask(0x07, 0x10, 0x10)
        writeTunerMask(0x0C, 0x0B, 0x9F)
    }

    private fun setDemodAgc(on: Boolean) = setDemodReg(0, 0x19, if (on) 0x25 else 0x05)

    private fun resetBuffer() {
        setUsbReg(USB_EPA_CTL, 0x1002, 2)
        setUsbReg(USB_EPA_CTL, 0x0000, 2)
    }
}
