package com.flockyou.privilege

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

data class MagiskDiagnosticCapabilities(
    val companionAvailable: Boolean,
    val shannonStreamAvailable: Boolean,
    val mediaTekCcciStreamAvailable: Boolean
)

/** App-side contract for the optional authenticated Magisk diagnostic companion. */
object MagiskDiagnosticCompanion {
    private const val SOCKET_NAME = "flockyou_diag"
    private const val OP_PING: Int = 0x01
    private const val OP_SHANNON_STREAM: Int = 0x02
    private const val OP_CCCI_STREAM: Int = 0x03
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'Y'.code.toByte(), 'D'.code.toByte(), '1'.code.toByte())

    fun probe(timeoutMs: Int = 350): Boolean = probeCapabilities(timeoutMs).companionAvailable

    fun probeCapabilities(timeoutMs: Int = 350): MagiskDiagnosticCapabilities = runCatching {
        openSocket(timeoutMs).use { socket ->
            socket.outputStream.write(OP_PING)
            socket.outputStream.flush()
            val header = readHeader(socket.inputStream)
            val caps = header[5].toInt() and 0xff
            MagiskDiagnosticCapabilities(
                companionAvailable = header[4].toInt() == 0,
                shannonStreamAvailable = (caps and 0x01) != 0,
                mediaTekCcciStreamAvailable = (caps and 0x02) != 0
            )
        }
    }.getOrElse { MagiskDiagnosticCapabilities(false, false, false) }

    @Throws(IOException::class)
    fun openCcciStream(timeoutMs: Int = 1_500): InputStream = openStream(OP_CCCI_STREAM, "MediaTek CCCI", timeoutMs)

    @Throws(IOException::class)
    fun openShannonStream(timeoutMs: Int = 1_500): InputStream = openStream(OP_SHANNON_STREAM, "Shannon", timeoutMs)

    private fun openStream(op: Int, label: String, timeoutMs: Int): InputStream {
        val socket = openSocket(timeoutMs)
        try {
            socket.outputStream.write(op)
            socket.outputStream.flush()
            val status = readHeader(socket.inputStream)[4].toInt() and 0xff
            if (status != 0) throw IOException("Companion rejected $label stream with status $status")
            return object : FilterInputStream(socket.inputStream) {
                override fun close() {
                    try { super.close() } finally { runCatching { socket.close() } }
                }
            }
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    private fun openSocket(timeoutMs: Int): LocalSocket = LocalSocket().also { socket ->
        socket.soTimeout = timeoutMs
        socket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
    }

    private fun readHeader(input: InputStream): ByteArray {
        val header = ByteArray(8)
        var offset = 0
        while (offset < header.size) {
            val n = input.read(header, offset, header.size - offset)
            if (n < 0) throw IOException("Companion closed before status header")
            offset += n
        }
        if (!header.copyOfRange(0, 4).contentEquals(MAGIC)) throw IOException("Invalid companion protocol magic")
        return header
    }
}
