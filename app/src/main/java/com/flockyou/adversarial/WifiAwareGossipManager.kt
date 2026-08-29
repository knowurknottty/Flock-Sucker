package com.flockyou.adversarial

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.*
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import javax.crypto.KeyAgreement
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class MeshGossipState(
    val supported: Boolean = false,
    val active: Boolean = false,
    val peersSeen: Int = 0,
    val encryptedSyncs: Int = 0,
    val lastError: String? = null,
    val lastReceivedBloomPopulation: Int? = null,
    val status: String = "Idle"
)

class BloomSummary(private val bytes: ByteArray = ByteArray(BYTE_COUNT)) {
    companion object {
        const val BYTE_COUNT = 20 // 160 bits; leaves room for E2EE framing under NAN message limits.
        private const val HASHES = 4

        fun fromTokens(tokens: Collection<String>): BloomSummary {
            val bloom = BloomSummary()
            tokens.forEach(bloom::add)
            return bloom
        }

        fun fromBytes(bytes: ByteArray): BloomSummary {
            require(bytes.size == BYTE_COUNT)
            return BloomSummary(bytes.copyOf())
        }
    }

    fun add(token: String) {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        repeat(HASHES) { i ->
            val v = ((digest[i * 2].toInt() and 0xff) shl 8) or (digest[i * 2 + 1].toInt() and 0xff)
            val bit = v % (BYTE_COUNT * 8)
            bytes[bit / 8] = (bytes[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
        }
    }

    fun mightContain(token: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return (0 until HASHES).all { i ->
            val v = ((digest[i * 2].toInt() and 0xff) shl 8) or (digest[i * 2 + 1].toInt() and 0xff)
            val bit = v % (BYTE_COUNT * 8)
            (bytes[bit / 8].toInt() and (1 shl (bit % 8))) != 0
        }
    }

    fun population(): Int = bytes.sumOf { b -> Integer.bitCount(b.toInt() and 0xff) }
    fun toByteArray(): ByteArray = bytes.copyOf()
}

/**
 * Zero-cloud Wi-Fi Aware gossip. Discovery messages carry only ephemeral public keys and an
 * AES-GCM encrypted compact Bloom summary; no account/device identifier is transmitted.
 *
 * NAN messages are lossy and small, so this is intentionally a summary exchange rather than
 * a database replication protocol.
 */
class WifiAwareGossipManager(private val context: Context) {
    companion object {
        private const val SERVICE_NAME = "flockyou-alpr-v1"
        private const val MSG_HELLO = 1
        private const val MSG_BLOOM = 2
    }

    private val aware = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private var keyPair: KeyPair? = null
    private var localBloom = BloomSummary()
    private val peerKeys = ConcurrentHashMap<PeerHandle, ByteArray>()
    private val peers = ConcurrentHashMap.newKeySet<PeerHandle>()
    private val secureRandom = SecureRandom()
    private val messageIds = AtomicInteger(100)
    private val _state = MutableStateFlow(
        MeshGossipState(
            supported = aware?.isAvailable == true,
            status = if (aware?.isAvailable == true) "Ready" else "Wi-Fi Aware unsupported"
        )
    )
    val state: StateFlow<MeshGossipState> = _state.asStateFlow()

    fun updateLocalTokens(tokens: Collection<String>) {
        localBloom = BloomSummary.fromTokens(tokens)
    }

    fun start() {
        val manager = aware ?: return fail("Wi-Fi Aware service unavailable")
        if (!manager.isAvailable) return fail("Wi-Fi Aware currently unavailable")
        if (!hasPermission()) return fail("Nearby Wi-Fi permission not granted")
        if (_state.value.active) return
        try {
            keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            manager.attach(object : AttachCallback() {
                override fun onAttached(session: WifiAwareSession) {
                    awareSession = session
                    _state.value = _state.value.copy(active = true, status = "NAN mesh active", lastError = null)
                    startPublishAndSubscribe(session)
                }

                override fun onAttachFailed() = fail("Wi-Fi Aware attach failed")
            }, null)
        } catch (t: Throwable) {
            fail("Wi-Fi Aware start failed: ${t.javaClass.simpleName}")
        }
    }

    fun stop() {
        publishSession?.close()
        subscribeSession?.close()
        awareSession?.close()
        publishSession = null
        subscribeSession = null
        awareSession = null
        peerKeys.clear()
        peers.clear()
        _state.value = _state.value.copy(active = false, peersSeen = 0, status = "Mesh stopped")
    }

    private fun startPublishAndSubscribe(session: WifiAwareSession) {
        if (!hasPermission()) {
            fail("Nearby Wi-Fi permission revoked before discovery start")
            return
        }
        try {
            val pub = PublishConfig.Builder().setServiceName(SERVICE_NAME).build()
            session.publish(pub, object : DiscoverySessionCallback() {
                override fun onPublishStarted(s: PublishDiscoverySession) {
                    publishSession = s
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    handleMessage(publishSession, peerHandle, message)
                }
            }, null)

            val sub = SubscribeConfig.Builder().setServiceName(SERVICE_NAME).build()
            session.subscribe(sub, object : DiscoverySessionCallback() {
                override fun onSubscribeStarted(s: SubscribeDiscoverySession) {
                    subscribeSession = s
                }

                override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray, matchFilter: List<ByteArray>) {
                    observePeer(peerHandle)
                    sendHello(subscribeSession, peerHandle)
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    handleMessage(subscribeSession, peerHandle, message)
                }
            }, null)
        } catch (_: SecurityException) {
            fail("Nearby Wi-Fi permission unavailable during discovery start")
        }
    }

    private fun observePeer(peer: PeerHandle) {
        peers += peer
        _state.value = _state.value.copy(peersSeen = peers.size)
    }

    private fun sendHello(session: DiscoverySession?, peer: PeerHandle) {
        val pub = keyPair?.public?.encoded ?: return
        val payload = ByteBuffer.allocate(1 + pub.size).put(MSG_HELLO.toByte()).put(pub).array()
        // EC public keys are typically ~91 bytes, safely under the NAN lightweight message budget.
        if (payload.size > 240) {
            fail("Ephemeral public key exceeds NAN message budget")
            return
        }
        try {
            session?.sendMessage(peer, messageIds.incrementAndGet(), payload)
        } catch (_: SecurityException) {
            fail("Nearby Wi-Fi permission unavailable while sending hello")
        }
    }

    private fun sendBloom(session: DiscoverySession?, peer: PeerHandle) {
        val secret = peerKeys[peer] ?: return
        try {
            val nonce = ByteArray(12).also(secureRandom::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            val encrypted = cipher.doFinal(localBloom.toByteArray())
            val payload = ByteBuffer.allocate(1 + nonce.size + encrypted.size)
                .put(MSG_BLOOM.toByte()).put(nonce).put(encrypted).array()
            try {
                session?.sendMessage(peer, messageIds.incrementAndGet(), payload)
            } catch (_: SecurityException) {
                fail("Nearby Wi-Fi permission unavailable while sending Bloom summary")
                return
            }
        } catch (t: Throwable) {
            fail("Mesh encryption failed: ${t.javaClass.simpleName}")
        }
    }

    private fun handleMessage(session: DiscoverySession?, peer: PeerHandle, message: ByteArray) {
        if (message.isEmpty()) return
        observePeer(peer)
        when (message[0].toInt()) {
            MSG_HELLO -> {
                try {
                    val remotePublic = KeyFactory.getInstance("EC")
                        .generatePublic(X509EncodedKeySpec(message.copyOfRange(1, message.size)))
                    val derived = deriveAesKey(remotePublic)
                    val prior = peerKeys.putIfAbsent(peer, derived)
                    if (prior != null && !MessageDigest.isEqual(prior, derived)) {
                        fail("Peer ephemeral key changed within one NAN session; refusing re-key")
                        return
                    }
                    sendBloom(session, peer)
                } catch (t: Throwable) {
                    fail("Peer key rejected: ${t.javaClass.simpleName}")
                }
            }
            MSG_BLOOM -> {
                val key = peerKeys[peer] ?: run {
                    sendHello(session, peer)
                    return
                }
                if (message.size < 1 + 12 + 16) return
                try {
                    val nonce = message.copyOfRange(1, 13)
                    val ciphertext = message.copyOfRange(13, message.size)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                    val bloom = BloomSummary.fromBytes(cipher.doFinal(ciphertext))
                    _state.value = _state.value.copy(
                        encryptedSyncs = _state.value.encryptedSyncs + 1,
                        lastReceivedBloomPopulation = bloom.population(),
                        status = "Encrypted Bloom summary received"
                    )
                } catch (t: Throwable) {
                    fail("Encrypted gossip rejected: ${t.javaClass.simpleName}")
                }
            }
        }
    }

    private fun deriveAesKey(remotePublic: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(requireNotNull(keyPair).private)
        agreement.doPhase(remotePublic, true)
        return MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret())
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(lastError = message, status = message)
    }
}
