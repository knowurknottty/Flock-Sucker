package com.flockyou.adversarial

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pure framing used by the BLE fallback so the transport remains testable without Android. */
object BleMeshFrameCodec {
    private const val MAGIC: Byte = 0xF1.toByte()
    private const val HEADER = 5

    fun fragment(message: ByteArray, messageId: Int, maxFrameBytes: Int): List<ByteArray> {
        require(message.isNotEmpty())
        require(maxFrameBytes > HEADER)
        val body = maxFrameBytes - HEADER
        val count = (message.size + body - 1) / body
        require(count in 1..255)
        return (0 until count).map { index ->
            val from = index * body
            val to = minOf(message.size, from + body)
            ByteBuffer.allocate(HEADER + to - from)
                .put(MAGIC)
                .putShort((messageId and 0xffff).toShort())
                .put(index.toByte())
                .put(count.toByte())
                .put(message, from, to - from)
                .array()
        }
    }

    fun decodeHeader(frame: ByteArray): Header? {
        if (frame.size <= HEADER || frame[0] != MAGIC) return null
        val id = ((frame[1].toInt() and 0xff) shl 8) or (frame[2].toInt() and 0xff)
        val index = frame[3].toInt() and 0xff
        val count = frame[4].toInt() and 0xff
        if (count == 0 || index >= count) return null
        return Header(id, index, count, frame.copyOfRange(HEADER, frame.size))
    }

    data class Header(val messageId: Int, val index: Int, val count: Int, val payload: ByteArray)
}

class BleMeshReassembler(private val maxAssemblies: Int = 32) {
    private data class Assembly(val count: Int, val chunks: Array<ByteArray?>, var touched: Long)
    private val assemblies = LinkedHashMap<String, Assembly>()

    @Synchronized
    fun accept(peer: String, frame: ByteArray, nowMs: Long = System.currentTimeMillis()): ByteArray? {
        val h = BleMeshFrameCodec.decodeHeader(frame) ?: return null
        val key = "$peer:${h.messageId}"
        val assembly = assemblies.getOrPut(key) {
            while (assemblies.size >= maxAssemblies) assemblies.remove(assemblies.keys.first())
            Assembly(h.count, arrayOfNulls(h.count), nowMs)
        }
        if (assembly.count != h.count) {
            assemblies.remove(key)
            return null
        }
        assembly.touched = nowMs
        assembly.chunks[h.index] = h.payload
        if (assembly.chunks.any { it == null }) return null
        assemblies.remove(key)
        val size = assembly.chunks.sumOf { it!!.size }
        return ByteBuffer.allocate(size).also { out -> assembly.chunks.forEach { out.put(it!!) } }.array()
    }

    @Synchronized
    fun clear() = assemblies.clear()
}

/**
 * BLE GATT fallback for devices without usable Wi-Fi Aware/NAN.
 * Only a fixed service UUID, ephemeral ECDH public keys, and AES-GCM encrypted Bloom summaries
 * cross the air. The transport intentionally carries no account/device identifier or raw coordinates.
 */
class BleGattGossipManager(private val context: Context) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8d7c2a4c-f10c-4fb9-9c57-79cced7672e0")
        val EXCHANGE_UUID: UUID = UUID.fromString("a49eb457-01e6-47ab-9f7f-8e2302f46a21")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MSG_HELLO = 1
        private const val MSG_BLOOM = 2
        private const val DEFAULT_FRAME_BYTES = 20
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter = bluetoothManager?.adapter
    private val advertiser get() = adapter?.bluetoothLeAdvertiser
    private val scanner get() = adapter?.bluetoothLeScanner
    private val secureRandom = SecureRandom()
    private val messageIds = AtomicInteger(1)
    private val reassembler = BleMeshReassembler()
    private var keyPair: KeyPair? = null
    private var localBloom = BloomSummary()
    private var gattServer: BluetoothGattServer? = null
    private var exchangeCharacteristic: BluetoothGattCharacteristic? = null
    private val clients = ConcurrentHashMap<String, BluetoothGatt>()
    private val peerKeys = ConcurrentHashMap<String, ByteArray>()
    private val mtus = ConcurrentHashMap<String, Int>()
    private val peers = ConcurrentHashMap.newKeySet<String>()
    private val clientWriteQueues = ConcurrentHashMap<String, ArrayDeque<ByteArray>>()
    private val serverNotifyQueues = ConcurrentHashMap<String, ArrayDeque<ByteArray>>()
    private val clientWriteBusy = ConcurrentHashMap.newKeySet<String>()
    private val serverNotifyBusy = ConcurrentHashMap.newKeySet<String>()

    private val _state = MutableStateFlow(
        MeshGossipState(
            supported = adapter?.isEnabled == true && advertiser != null && scanner != null,
            status = if (adapter?.isEnabled == true && advertiser != null && scanner != null) {
                "BLE GATT fallback ready"
            } else "BLE GATT advertising/scanning unavailable"
        )
    )
    val state: StateFlow<MeshGossipState> = _state.asStateFlow()

    fun updateLocalTokens(tokens: Collection<String>) {
        localBloom = BloomSummary.fromTokens(tokens)
    }

    fun start() {
        if (_state.value.active) return
        val manager = bluetoothManager ?: return fail("BluetoothManager unavailable")
        val bt = adapter ?: return fail("Bluetooth adapter unavailable")
        if (!bt.isEnabled) return fail("Bluetooth is disabled")
        if (advertiser == null || scanner == null) return fail("BLE advertiser/scanner unavailable")
        if (!hasPermissions()) return fail("BLE mesh permissions not granted")

        try {
            keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            val server = manager.openGattServer(context, serverCallback)
                ?: return fail("Unable to open BLE GATT server")
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val exchange = BluetoothGattCharacteristic(
                EXCHANGE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val cccd = BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            exchange.addDescriptor(cccd)
            service.addCharacteristic(exchange)
            if (!server.addService(service)) {
                server.close()
                return fail("Unable to register BLE mesh GATT service")
            }
            gattServer = server
            exchangeCharacteristic = exchange
            startAdvertising()
            startScanning()
            _state.value = _state.value.copy(active = true, lastError = null, status = "BLE GATT fallback mesh active")
        } catch (e: SecurityException) {
            stop()
            fail("BLE mesh permission unavailable: ${e.javaClass.simpleName}")
        } catch (t: Throwable) {
            stop()
            fail("BLE mesh start failed: ${t.javaClass.simpleName}")
        }
    }

    fun stop() {
        try { scanner?.stopScan(scanCallback) } catch (_: Throwable) {}
        try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Throwable) {}
        clients.values.forEach { gatt ->
            try { gatt.disconnect() } catch (_: Throwable) {}
            try { gatt.close() } catch (_: Throwable) {}
        }
        clients.clear()
        try { gattServer?.close() } catch (_: Throwable) {}
        gattServer = null
        exchangeCharacteristic = null
        peerKeys.clear()
        peers.clear()
        mtus.clear()
        clientWriteQueues.clear()
        serverNotifyQueues.clear()
        clientWriteBusy.clear()
        serverNotifyBusy.clear()
        reassembler.clear()
        _state.value = _state.value.copy(active = false, peersSeen = 0, status = "BLE mesh stopped")
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun startScanning() {
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) = fail("BLE advertising failed: $errorCode")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val peer = device.address ?: return
            if (clients.containsKey(peer)) return
            observePeer(peer)
            try {
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    @Suppress("DEPRECATION")
                    device.connectGatt(context, false, clientCallback)
                }
                if (gatt != null) clients.putIfAbsent(peer, gatt)
            } catch (_: SecurityException) {
                fail("BLE connect permission unavailable")
            }
        }

        override fun onScanFailed(errorCode: Int) = fail("BLE mesh scan failed: $errorCode")
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val peer = gatt.device.address
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                clients.remove(peer)
                peerKeys.remove(peer)
                try { gatt.close() } catch (_: Throwable) {}
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                observePeer(peer)
                try {
                    if (!gatt.requestMtu(185)) gatt.discoverServices()
                } catch (_: SecurityException) {
                    fail("BLE connect permission unavailable during MTU negotiation")
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            mtus[gatt.device.address] = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            try { gatt.discoverServices() } catch (_: SecurityException) { fail("BLE service discovery permission unavailable") }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(EXCHANGE_UUID) ?: return
            try {
                gatt.setCharacteristicNotification(characteristic, true)
                val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            } catch (_: SecurityException) {
                fail("BLE notification permission unavailable")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == CCCD_UUID) {
                sendHelloAsClient(gatt)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val peer = gatt.device.address
            clientWriteBusy.remove(peer)
            if (status == BluetoothGatt.GATT_SUCCESS) pumpClientWrites(peer, gatt)
            else fail("BLE mesh write failed: $status")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            acceptFrame(gatt.device.address, value) { response -> queueClientMessage(gatt, response) }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            acceptFrame(gatt.device.address, characteristic.value ?: return) { response -> queueClientMessage(gatt, response) }
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val peer = device.address
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) observePeer(peer)
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                peerKeys.remove(peer)
                serverNotifyQueues.remove(peer)
                serverNotifyBusy.remove(peer)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            mtus[device.address] = mtu
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value) } catch (_: SecurityException) {}
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value) } catch (_: SecurityException) {}
            }
            if (characteristic.uuid != EXCHANGE_UUID || offset != 0) return
            acceptFrame(device.address, value) { response -> queueServerMessage(device, response) }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            val peer = device.address
            serverNotifyBusy.remove(peer)
            if (status == BluetoothGatt.GATT_SUCCESS) pumpServerNotifications(peer, device)
            else fail("BLE mesh notify failed: $status")
        }
    }

    private fun sendHelloAsClient(gatt: BluetoothGatt) {
        val pub = keyPair?.public?.encoded ?: return
        queueClientMessage(gatt, byteArrayOf(MSG_HELLO.toByte()) + pub)
    }

    private fun acceptFrame(peer: String, frame: ByteArray, reply: (ByteArray) -> Unit) {
        val message = reassembler.accept(peer, frame) ?: return
        when (message.firstOrNull()?.toInt()) {
            MSG_HELLO -> {
                try {
                    val remote = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(message.copyOfRange(1, message.size)))
                    val derived = deriveAesKey(remote)
                    val prior = peerKeys.putIfAbsent(peer, derived)
                    if (prior != null && !MessageDigest.isEqual(prior, derived)) {
                        fail("BLE peer key changed within one connection; refusing re-key")
                        return
                    }
                    val pub = keyPair?.public?.encoded ?: return
                    reply(byteArrayOf(MSG_HELLO.toByte()) + pub)
                    reply(encryptedBloom(peer))
                } catch (t: Throwable) {
                    fail("BLE peer key rejected: ${t.javaClass.simpleName}")
                }
            }
            MSG_BLOOM -> decryptBloom(peer, message)
        }
    }

    private fun encryptedBloom(peer: String): ByteArray {
        val secret = requireNotNull(peerKeys[peer])
        val nonce = ByteArray(12).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(localBloom.toByteArray())
        return byteArrayOf(MSG_BLOOM.toByte()) + nonce + encrypted
    }

    private fun decryptBloom(peer: String, message: ByteArray) {
        val secret = peerKeys[peer] ?: return
        if (message.size < 1 + 12 + 16) return
        try {
            val nonce = message.copyOfRange(1, 13)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            val bloom = BloomSummary.fromBytes(cipher.doFinal(message.copyOfRange(13, message.size)))
            _state.value = _state.value.copy(
                encryptedSyncs = _state.value.encryptedSyncs + 1,
                lastReceivedBloomPopulation = bloom.population(),
                status = "BLE GATT encrypted Bloom summary received"
            )
        } catch (t: Throwable) {
            fail("BLE encrypted gossip rejected: ${t.javaClass.simpleName}")
        }
    }

    private fun queueClientMessage(gatt: BluetoothGatt, message: ByteArray) {
        val peer = gatt.device.address
        val frameBytes = frameBytes(peer)
        val queue = clientWriteQueues.getOrPut(peer) { ArrayDeque() }
        synchronized(queue) { BleMeshFrameCodec.fragment(message, messageIds.getAndIncrement(), frameBytes).forEach(queue::addLast) }
        pumpClientWrites(peer, gatt)
    }

    private fun pumpClientWrites(peer: String, gatt: BluetoothGatt) {
        if (!clientWriteBusy.add(peer)) return
        val queue = clientWriteQueues[peer]
        if (queue == null) {
            clientWriteBusy.remove(peer)
            return
        }
        val frame: ByteArray? = synchronized(queue) { if (queue.isEmpty()) null else queue.removeFirst() }
        if (frame == null) {
            clientWriteBusy.remove(peer)
            return
        }
        val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(EXCHANGE_UUID)
        if (characteristic == null || !writeCharacteristic(gatt, characteristic, frame)) {
            clientWriteBusy.remove(peer)
            fail("BLE mesh characteristic write could not start")
        }
    }

    private fun queueServerMessage(device: BluetoothDevice, message: ByteArray) {
        val peer = device.address
        val queue = serverNotifyQueues.getOrPut(peer) { ArrayDeque() }
        synchronized(queue) { BleMeshFrameCodec.fragment(message, messageIds.getAndIncrement(), frameBytes(peer)).forEach(queue::addLast) }
        pumpServerNotifications(peer, device)
    }

    private fun pumpServerNotifications(peer: String, device: BluetoothDevice) {
        if (!serverNotifyBusy.add(peer)) return
        val queue = serverNotifyQueues[peer]
        if (queue == null) {
            serverNotifyBusy.remove(peer)
            return
        }
        val frame: ByteArray? = synchronized(queue) { if (queue.isEmpty()) null else queue.removeFirst() }
        if (frame == null) {
            serverNotifyBusy.remove(peer)
            return
        }
        val server = gattServer
        val characteristic = exchangeCharacteristic
        if (server == null || characteristic == null || !notify(server, device, characteristic, frame)) {
            serverNotifyBusy.remove(peer)
            fail("BLE mesh notification could not start")
        }
    }

    private fun frameBytes(peer: String): Int = ((mtus[peer] ?: 23) - 3).coerceIn(DEFAULT_FRAME_BYTES, 180)

    private fun writeCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        } catch (_: SecurityException) { false }
    }

    private fun notify(
        server: BluetoothGattServer, device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic, value: ByteArray
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, false, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    } catch (_: SecurityException) { false }

    private fun deriveAesKey(remotePublic: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(requireNotNull(keyPair).private)
        agreement.doPhase(remotePublic, true)
        return MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret())
    }

    private fun observePeer(peer: String) {
        peers += peer
        _state.value = _state.value.copy(peersSeen = peers.size)
    }

    private fun hasPermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(lastError = message, status = message)
    }
}
